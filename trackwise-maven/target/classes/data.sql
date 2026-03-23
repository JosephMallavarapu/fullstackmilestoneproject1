-- ═══ TrackWise Seed Data ═══
-- Runs on every startup via spring.sql.init.mode=always
-- Uses INSERT IGNORE to avoid duplicates on re-start

-- ─── Roles ───────────────────────────────────────────────────
INSERT IGNORE INTO roles (id, name, description) VALUES (1, 'ADMIN',    'Administrator with full access');
INSERT IGNORE INTO roles (id, name, description) VALUES (2, 'MANAGER',  'Department manager, can approve expenses');
INSERT IGNORE INTO roles (id, name, description) VALUES (3, 'EMPLOYEE', 'Regular employee, submits expenses');

-- ─── Departments ─────────────────────────────────────────────
INSERT IGNORE INTO departments (id, name, code) VALUES (1, 'General',     'GEN');
INSERT IGNORE INTO departments (id, name, code) VALUES (2, 'Engineering', 'ENG');
INSERT IGNORE INTO departments (id, name, code) VALUES (3, 'Marketing',   'MKT');
INSERT IGNORE INTO departments (id, name, code) VALUES (4, 'Sales',       'SAL');
INSERT IGNORE INTO departments (id, name, code) VALUES (5, 'Finance',     'FIN');

-- ─── Categories ──────────────────────────────────────────────
INSERT IGNORE INTO categories (id, name, code, is_active) VALUES (1, 'Travel',    'TRV',  true);
INSERT IGNORE INTO categories (id, name, code, is_active) VALUES (2, 'Meals',     'MEAL', true);
INSERT IGNORE INTO categories (id, name, code, is_active) VALUES (3, 'Supplies',  'SUP',  true);
INSERT IGNORE INTO categories (id, name, code, is_active) VALUES (4, 'Software',  'SW',   true);
INSERT IGNORE INTO categories (id, name, code, is_active) VALUES (5, 'Equipment', 'EQP',  true);
INSERT IGNORE INTO categories (id, name, code, is_active) VALUES (6, 'Training',  'TRN',  true);

-- ─── Default Policy Rules ────────────────────────────────────
INSERT IGNORE INTO policy_rules (id, name, description, rule_type, action, threshold, is_active) VALUES
(1, 'Max Single Expense', 'Flag expenses above $5000',   'AMOUNT_LIMIT', 'FLAG',   5000.00,  true),
(2, 'High Amount Alert',  'Reject expenses above $25000','AMOUNT_LIMIT', 'REJECT', 25000.00, true);

-- ─── Default Admin Account ───────────────────────────────────
-- Password: admin123  (BCrypt encoded)
-- Login: admin@trackwise.com / admin123
INSERT IGNORE INTO users (first_name, last_name, email, password_hash, role_id, department_id, is_active, failed_logins)
VALUES ('Admin', 'TrackWise', 'admin@trackwise.com',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LPVyc66T.6.',
        1, 1, true, 0);
