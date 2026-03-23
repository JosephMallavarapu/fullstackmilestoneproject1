-- ============================================================
--  TrackWise: Relational Expense Analytics Platform
--  MySQL Database Schema — v2.0 (Industry Edition)
--  Engine: InnoDB | Charset: utf8mb4
--  Features: Policy Engine, OCR, Multi-Currency, ERP Sync,
--             Notifications, ISO 27001 Audit, PCI DSS Compliance
-- ============================================================

CREATE DATABASE IF NOT EXISTS trackwise
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE trackwise;

-- ─────────────────────────────────────────────────────────────
-- 1. ROLES
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS roles (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,   -- ADMIN, MANAGER, EMPLOYEE, AUDITOR
    description VARCHAR(255),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────
-- 2. DEPARTMENTS
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS departments (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    code       VARCHAR(20)  NOT NULL UNIQUE,    -- ENG, FIN, MKT, HR, OPS
    budget     DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────
-- 3. USERS
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    first_name     VARCHAR(80)  NOT NULL,
    last_name      VARCHAR(80)  NOT NULL,
    email          VARCHAR(180) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    department_id  BIGINT UNSIGNED,
    role_id        BIGINT UNSIGNED NOT NULL,
    manager_id     BIGINT UNSIGNED,                -- self-referential hierarchy
    employee_code  VARCHAR(30) UNIQUE,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    failed_logins  TINYINT UNSIGNED NOT NULL DEFAULT 0,  -- PCI DSS lockout
    locked_until   TIMESTAMP NULL,                        -- PCI DSS lockout
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_dept    FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL,
    CONSTRAINT fk_user_role    FOREIGN KEY (role_id)       REFERENCES roles(id),
    CONSTRAINT fk_user_manager FOREIGN KEY (manager_id)    REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────
-- 4. CATEGORIES
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS categories (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL UNIQUE,
    code          VARCHAR(20)  NOT NULL UNIQUE,
    default_limit DECIMAL(12,2),          -- per-expense soft cap
    icon          VARCHAR(50),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────
-- 5. BUDGET GOALS  (department × category × period)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS budget_goals (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    department_id    BIGINT UNSIGNED NOT NULL,
    category_id      BIGINT UNSIGNED NOT NULL,
    fiscal_year      SMALLINT UNSIGNED NOT NULL,
    fiscal_quarter   TINYINT  UNSIGNED NOT NULL CHECK (fiscal_quarter BETWEEN 1 AND 4),
    allocated_amount DECIMAL(15,2) NOT NULL,
    created_by       BIGINT UNSIGNED NOT NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_budget (department_id, category_id, fiscal_year, fiscal_quarter),
    CONSTRAINT fk_bg_dept FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_bg_cat  FOREIGN KEY (category_id)   REFERENCES categories(id),
    CONSTRAINT fk_bg_user FOREIGN KEY (created_by)    REFERENCES users(id)
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────
-- 6. CURRENCY RATES  (cached FX, refreshed hourly)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS currency_rates (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    base_currency CHAR(3) NOT NULL DEFAULT 'USD',
    target_currency CHAR(3) NOT NULL,
    rate         DECIMAL(16,8) NOT NULL,         -- 1 base = rate target
    source       VARCHAR(50) NOT NULL DEFAULT 'ECB',
    fetched_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_cur_pair (base_currency, target_currency)
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────
-- 7. EXPENSES  (core fact table)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS expenses (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    reference_code VARCHAR(30)  NOT NULL UNIQUE,        -- EXP-YYYYMMDD-XXXXX
    submitted_by   BIGINT UNSIGNED NOT NULL,
    department_id  BIGINT UNSIGNED NOT NULL,
    category_id    BIGINT UNSIGNED NOT NULL,
    title          VARCHAR(255) NOT NULL,
    description    TEXT,
    vendor         VARCHAR(255),
    amount         DECIMAL(12,2) NOT NULL,
    currency       CHAR(3) NOT NULL DEFAULT 'USD',
    amount_usd     DECIMAL(12,2) NOT NULL,              -- normalised to USD
    exchange_rate  DECIMAL(10,6) NOT NULL DEFAULT 1.000000,
    expense_date   DATE NOT NULL,
    receipt_url    VARCHAR(500),
    ocr_scan_id    BIGINT UNSIGNED,                    -- FK to ocr_scans
    status         ENUM('DRAFT','PENDING','APPROVED','REJECTED','REIMBURSED') NOT NULL DEFAULT 'DRAFT',
    is_recurring   BOOLEAN NOT NULL DEFAULT FALSE,
    policy_status  ENUM('CLEAN','FLAGGED','REJECTED') NOT NULL DEFAULT 'CLEAN',
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_exp_user   FOREIGN KEY (submitted_by)  REFERENCES users(id),
    CONSTRAINT fk_exp_dept   FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_exp_cat    FOREIGN KEY (category_id)   REFERENCES categories(id)
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────
-- 8. EXPENSE APPROVALS  (multi-level approval chain)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS expense_approvals (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    expense_id  BIGINT UNSIGNED NOT NULL,
    approver_id BIGINT UNSIGNED NOT NULL,
    level       TINYINT UNSIGNED NOT NULL DEFAULT 1,   -- 1=Manager, 2=Finance, 3=Director
    action      ENUM('PENDING','APPROVED','REJECTED','ESCALATED') NOT NULL DEFAULT 'PENDING',
    remarks     TEXT,
    actioned_at TIMESTAMP NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appr_exp  FOREIGN KEY (expense_id)  REFERENCES expenses(id) ON DELETE CASCADE,
    CONSTRAINT fk_appr_user FOREIGN KEY (approver_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────
-- 9. TAGS & EXPENSE_TAGS
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tags (
    id   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS expense_tags (
    expense_id BIGINT UNSIGNED NOT NULL,
    tag_id     BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (expense_id, tag_id),
    CONSTRAINT fk_et_exp FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE,
    CONSTRAINT fk_et_tag FOREIGN KEY (tag_id)     REFERENCES tags(id)     ON DELETE CASCADE
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────
-- 10. POLICY RULES  (configurable auto-enforce engine)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS policy_rules (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150) NOT NULL UNIQUE,
    description TEXT,
    rule_type   ENUM(
        'AMOUNT_LIMIT',          -- single transaction cap
        'DAILY_CAP',             -- daily per-user total
        'RECEIPT_REQUIRED',      -- receipt mandatory above threshold
        'DUPLICATE_DETECTION',   -- same vendor+amount within N days
        'WEEKEND_BLOCK',         -- flag weekend expenses
        'CATEGORY_BUDGET',       -- category budget exceeded
        'CUSTOM'
    ) NOT NULL,
    severity    ENUM('INFO','WARNING','CRITICAL') NOT NULL DEFAULT 'WARNING',
    action      ENUM('FLAG','AUTO_REJECT') NOT NULL DEFAULT 'FLAG',
    threshold   DECIMAL(15,2),                        -- amount / days / %
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_by  BIGINT UNSIGNED,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pr_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────
-- 11. POLICY VIOLATIONS  (audit trail of every rule evaluation)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS policy_violations (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    expense_id     BIGINT UNSIGNED NOT NULL,
    rule_id        BIGINT UNSIGNED NOT NULL,
    result         ENUM('PASS','FLAGGED','REJECTED') NOT NULL,
    detail         TEXT,                               -- human-readable reason
    evaluated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pv_expense FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE,
    CONSTRAINT fk_pv_rule    FOREIGN KEY (rule_id)    REFERENCES policy_rules(id)
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────
-- 12. OCR SCANS  (receipt scanning metadata)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ocr_scans (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    submitted_by    BIGINT UNSIGNED NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    file_url        VARCHAR(500),
    provider        ENUM('TESSERACT','AWS_TEXTRACT','GOOGLE_VISION') NOT NULL DEFAULT 'TESSERACT',
    status          ENUM('PENDING','PROCESSING','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING',
    confidence_score DECIMAL(5,2),                    -- 0.00 – 100.00
    extracted_fields JSON,                            -- {vendor, amount, date, tax, currency}
    raw_text        LONGTEXT,
    error_message   VARCHAR(500),
    processed_at    TIMESTAMP NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ocr_user FOREIGN KEY (submitted_by) REFERENCES users(id)
) ENGINE=InnoDB;

-- Add OCR foreign key to expenses after both tables exist
ALTER TABLE expenses
    ADD CONSTRAINT fk_exp_ocr FOREIGN KEY (ocr_scan_id) REFERENCES ocr_scans(id) ON DELETE SET NULL;

-- ─────────────────────────────────────────────────────────────
-- 13. NOTIFICATIONS
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT UNSIGNED NOT NULL,
    type         ENUM(
        'EXPENSE_SUBMITTED','EXPENSE_APPROVED','EXPENSE_REJECTED',
        'APPROVAL_REQUIRED','POLICY_VIOLATION','BUDGET_ALERT',
        'ERP_SYNC_DONE','ERP_SYNC_ERROR','OCR_COMPLETE'
    ) NOT NULL,
    title        VARCHAR(255) NOT NULL,
    message      TEXT,
    channel      ENUM('EMAIL','SMS','PUSH','IN_APP') NOT NULL DEFAULT 'IN_APP',
    is_read      BOOLEAN NOT NULL DEFAULT FALSE,
    is_sent      BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at      TIMESTAMP NULL,
    related_id   BIGINT UNSIGNED,                     -- expense_id or approval_id
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────
-- 14. NOTIFICATION CHANNEL PREFERENCES  (per user)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notification_preferences (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT UNSIGNED NOT NULL,
    event_type  VARCHAR(50) NOT NULL,
    email       BOOLEAN NOT NULL DEFAULT TRUE,
    sms         BOOLEAN NOT NULL DEFAULT FALSE,
    push        BOOLEAN NOT NULL DEFAULT FALSE,
    in_app      BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE KEY uq_notif_pref (user_id, event_type),
    CONSTRAINT fk_np_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────
-- 15. ERP INTEGRATIONS  (QuickBooks, SAP Concur, Xero …)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS erp_integrations (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    provider        ENUM('QUICKBOOKS','SAP_CONCUR','XERO','NETSUITE') NOT NULL,
    display_name    VARCHAR(100) NOT NULL,
    client_id       VARCHAR(255),
    client_secret   VARCHAR(255),                     -- stored encrypted
    access_token    TEXT,
    refresh_token   TEXT,
    token_expires_at TIMESTAMP NULL,
    company_id      VARCHAR(100),                     -- e.g. QuickBooks realm ID
    is_active       BOOLEAN NOT NULL DEFAULT FALSE,
    last_synced_at  TIMESTAMP NULL,
    total_synced    INT UNSIGNED NOT NULL DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────
-- 16. ERP SYNC LOGS
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS erp_sync_logs (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    integration_id BIGINT UNSIGNED NOT NULL,
    expense_id     BIGINT UNSIGNED,
    action         VARCHAR(50) NOT NULL,              -- SYNC_OK, SYNC_WARN, SYNC_ERROR
    external_id    VARCHAR(255),                      -- QuickBooks txn ID
    message        TEXT,
    synced_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_esl_int FOREIGN KEY (integration_id) REFERENCES erp_integrations(id),
    CONSTRAINT fk_esl_exp FOREIGN KEY (expense_id)     REFERENCES expenses(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────
-- 17. AUDIT LOG  (ISO 27001 §A.12.4 — immutable)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_log (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    entity_type  VARCHAR(50) NOT NULL,               -- EXPENSE, APPROVAL, USER, POLICY, ERP
    entity_id    BIGINT UNSIGNED,
    action       VARCHAR(80) NOT NULL,               -- CREATE, UPDATE, DELETE, LOGIN, LOGOUT …
    old_value    JSON,
    new_value    JSON,
    performed_by BIGINT UNSIGNED,
    ip_address   VARCHAR(45),
    user_agent   VARCHAR(500),
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user FOREIGN KEY (performed_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────
-- INDEXES
-- ─────────────────────────────────────────────────────────────
CREATE INDEX idx_expenses_status       ON expenses(status);
CREATE INDEX idx_expenses_date         ON expenses(expense_date);
CREATE INDEX idx_expenses_dept_cat     ON expenses(department_id, category_id);
CREATE INDEX idx_expenses_submitted_by ON expenses(submitted_by);
CREATE INDEX idx_expenses_policy       ON expenses(policy_status);
CREATE INDEX idx_approvals_expense     ON expense_approvals(expense_id, level);
CREATE INDEX idx_audit_entity          ON audit_log(entity_type, entity_id);
CREATE INDEX idx_notifications_user    ON notifications(user_id, is_read);
CREATE INDEX idx_pv_expense            ON policy_violations(expense_id);
CREATE INDEX idx_currency_pair         ON currency_rates(base_currency, target_currency);
CREATE INDEX idx_erp_logs_sync         ON erp_sync_logs(integration_id, synced_at);
CREATE INDEX idx_ocr_user              ON ocr_scans(submitted_by, status);

-- ─────────────────────────────────────────────────────────────
-- ANALYTICS VIEWS
-- ─────────────────────────────────────────────────────────────

-- Monthly spend by department
CREATE OR REPLACE VIEW vw_monthly_spend_by_dept AS
SELECT
    d.name                              AS department,
    DATE_FORMAT(e.expense_date,'%Y-%m') AS month,
    c.name                              AS category,
    COUNT(e.id)                         AS total_expenses,
    SUM(e.amount_usd)                   AS total_amount_usd,
    AVG(e.amount_usd)                   AS avg_amount_usd
FROM expenses e
JOIN departments d ON e.department_id = d.id
JOIN categories  c ON e.category_id   = c.id
WHERE e.status IN ('APPROVED','REIMBURSED')
GROUP BY d.name, month, c.name;

-- Budget vs Actual per quarter
CREATE OR REPLACE VIEW vw_budget_vs_actual AS
SELECT
    d.name                                                       AS department,
    c.name                                                       AS category,
    bg.fiscal_year,
    bg.fiscal_quarter,
    bg.allocated_amount                                          AS budget,
    COALESCE(SUM(e.amount_usd), 0)                               AS actual_spent,
    bg.allocated_amount - COALESCE(SUM(e.amount_usd), 0)        AS remaining,
    ROUND(COALESCE(SUM(e.amount_usd),0) / bg.allocated_amount * 100, 2) AS utilization_pct
FROM budget_goals bg
JOIN departments d ON bg.department_id = d.id
JOIN categories  c ON bg.category_id   = c.id
LEFT JOIN expenses e
    ON  e.department_id   = bg.department_id
    AND e.category_id     = bg.category_id
    AND YEAR(e.expense_date)    = bg.fiscal_year
    AND QUARTER(e.expense_date) = bg.fiscal_quarter
    AND e.status IN ('APPROVED','REIMBURSED')
GROUP BY d.name, c.name, bg.fiscal_year, bg.fiscal_quarter, bg.allocated_amount;

-- Top spenders this month
CREATE OR REPLACE VIEW vw_top_spenders AS
SELECT
    u.id,
    CONCAT(u.first_name, ' ', u.last_name) AS employee,
    u.email,
    d.name                                  AS department,
    COUNT(e.id)                             AS total_claims,
    SUM(e.amount_usd)                       AS total_spent_usd
FROM expenses e
JOIN users       u ON e.submitted_by   = u.id
JOIN departments d ON e.department_id  = d.id
WHERE e.status IN ('APPROVED','REIMBURSED')
  AND MONTH(e.expense_date) = MONTH(CURRENT_DATE())
  AND YEAR(e.expense_date)  = YEAR(CURRENT_DATE())
GROUP BY u.id, employee, u.email, d.name
ORDER BY total_spent_usd DESC;

-- Policy violation summary
CREATE OR REPLACE VIEW vw_policy_violation_summary AS
SELECT
    pr.name         AS rule_name,
    pr.severity,
    COUNT(pv.id)    AS total_violations,
    SUM(pv.result = 'REJECTED') AS auto_rejected,
    SUM(pv.result = 'FLAGGED')  AS flagged
FROM policy_violations pv
JOIN policy_rules pr ON pv.rule_id = pr.id
GROUP BY pr.id, pr.name, pr.severity
ORDER BY total_violations DESC;

-- ─────────────────────────────────────────────────────────────
-- STORED PROCEDURE: generate reference code  EXP-YYYYMMDD-NNNNN
-- ─────────────────────────────────────────────────────────────
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS sp_next_reference_code(OUT ref_code VARCHAR(30))
BEGIN
    DECLARE prefix   VARCHAR(20);
    DECLARE seq_num  INT UNSIGNED;
    SET prefix = CONCAT('EXP-', DATE_FORMAT(CURRENT_DATE(),'%Y%m%d'));
    SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(reference_code, '-', -1) AS UNSIGNED)), 0) + 1
      INTO seq_num
      FROM expenses
     WHERE reference_code LIKE CONCAT(prefix, '-%');
    SET ref_code = CONCAT(prefix, '-', LPAD(seq_num, 5, '0'));
END$$
DELIMITER ;

-- ─────────────────────────────────────────────────────────────
-- SEED DATA
-- ─────────────────────────────────────────────────────────────

-- Roles
INSERT INTO roles (name, description) VALUES
  ('ADMIN',    'Full system access'),
  ('MANAGER',  'Approve team expenses, view department reports'),
  ('EMPLOYEE', 'Submit and track own expenses'),
  ('AUDITOR',  'Read-only access to all records')
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- Departments
INSERT INTO departments (name, code, budget) VALUES
  ('Engineering',    'ENG', 200000.00),
  ('Finance',        'FIN',  80000.00),
  ('Marketing',      'MKT', 120000.00),
  ('Human Resources','HR',   60000.00),
  ('Operations',     'OPS',  90000.00)
ON DUPLICATE KEY UPDATE budget = VALUES(budget);

-- Categories
INSERT INTO categories (name, code, default_limit, icon) VALUES
  ('Travel',        'TRV', 5000.00, '✈'),
  ('Software',      'SFT', 2000.00, '☁'),
  ('Hardware',      'HRD', 4000.00, '🖥'),
  ('Meals',         'MEL',  500.00, '🍽'),
  ('Marketing',     'MKT', 3000.00, '📣'),
  ('Training',      'TRN', 1500.00, '📚'),
  ('Office',        'OFC',  800.00, '🏢')
ON DUPLICATE KEY UPDATE default_limit = VALUES(default_limit);

-- Default admin user (password: Admin@1234 — BCrypt hash)
INSERT INTO users (first_name, last_name, email, password_hash, role_id, employee_code) VALUES
  ('Admin', 'User',   'admin@trackwise.io',
   '$2a$12$hQNz2.B5xZ5K5O1VQ.5i6OHfZ/G0VJlb8OdNDp8xZ9A1sLhZ7k8bm', 1, 'EMP-0001'),
  ('Alice',  'Johnson','alice@trackwise.io',
   '$2a$12$hQNz2.B5xZ5K5O1VQ.5i6OHfZ/G0VJlb8OdNDp8xZ9A1sLhZ7k8bm', 3, 'EMP-0002'),
  ('Bob',    'Kumar',  'bob@trackwise.io',
   '$2a$12$hQNz2.B5xZ5K5O1VQ.5i6OHfZ/G0VJlb8OdNDp8xZ9A1sLhZ7k8bm', 2, 'EMP-0003')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

-- Default Policy Rules
INSERT INTO policy_rules (name, description, rule_type, severity, action, threshold) VALUES
  ('Single Transaction Limit',
   'Auto-reject expenses exceeding $1,000 without pre-approval',
   'AMOUNT_LIMIT', 'CRITICAL', 'FLAG', 1000.00),
  ('Daily Spend Cap',
   'Flag if total daily spend per user exceeds $2,000',
   'DAILY_CAP', 'WARNING', 'FLAG', 2000.00),
  ('Receipt Required above $50',
   'Auto-reject if no receipt is attached for amounts over $50',
   'RECEIPT_REQUIRED', 'CRITICAL', 'AUTO_REJECT', 50.00),
  ('Duplicate Detection (7 days)',
   'Flag if same amount + vendor submitted within 7 days',
   'DUPLICATE_DETECTION', 'WARNING', 'FLAG', 7.00),
  ('Weekend Expense Flag',
   'Flag expenses dated on Saturday or Sunday for extra review',
   'WEEKEND_BLOCK', 'INFO', 'FLAG', NULL),
  ('Category Budget Exceeded',
   'Warn when category monthly spend exceeds allocated budget',
   'CATEGORY_BUDGET', 'WARNING', 'FLAG', NULL)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

-- Currency Rates (USD base — sample seed)
INSERT INTO currency_rates (base_currency, target_currency, rate, source) VALUES
  ('USD', 'EUR', 0.92340000, 'ECB'),
  ('USD', 'GBP', 0.78910000, 'ECB'),
  ('USD', 'INR', 83.42000000, 'RBI'),
  ('USD', 'JPY', 149.87000000, 'BOJ'),
  ('USD', 'CAD', 1.36120000, 'BOC'),
  ('USD', 'AUD', 1.52340000, 'RBA'),
  ('USD', 'SGD', 1.34500000, 'MAS'),
  ('USD', 'CHF', 0.89200000, 'SNB')
ON DUPLICATE KEY UPDATE rate = VALUES(rate), fetched_at = CURRENT_TIMESTAMP;

-- ERP Integrations (QuickBooks pre-configured as disabled)
INSERT INTO erp_integrations (provider, display_name, is_active) VALUES
  ('QUICKBOOKS', 'QuickBooks Online', FALSE),
  ('SAP_CONCUR', 'SAP Concur',        FALSE),
  ('XERO',       'Xero Accounting',   FALSE),
  ('NETSUITE',   'Oracle NetSuite',   FALSE)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;
