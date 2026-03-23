package com.trackwise.service.impl;

import com.trackwise.entity.*;
import com.trackwise.enums.ExpenseStatus;
import com.trackwise.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ═══════════════════════════════════════════════════════════
//  ExpenseService — full CRUD + approval workflow + analytics
//  Orchestrates PolicyService, CurrencyService, Notification,
//  ErpService and AuditLog writing.
// ═══════════════════════════════════════════════════════════
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenseRepo;
    private final UserRepository userRepo;
    private final DepartmentRepository deptRepo;
    private final CategoryRepository catRepo;
    private final ExpenseApprovalRepository approvalRepo;
    private final AuditLogRepository auditRepo;
    private final PolicyService policyService;
    private final CurrencyService currencyService;
    private final NotificationService notificationService;
    private final ErpService erpService;

    /** Create and submit a new expense. Runs policy check immediately. */
    public Expense createExpense(Long userId, Long departmentId, Long categoryId,
            String title, String description, String vendor,
            BigDecimal amount, String currency, LocalDate expenseDate,
            String receiptUrl) {

        log.info("Creating expense: userId={}, deptId={}, catId={}, title={}, amount={} {}",
                userId, departmentId, categoryId, title, amount, currency);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // Get or create default department
        Department dept = deptRepo.findById(departmentId).orElseGet(() -> {
            log.warn("Department {} not found, creating default", departmentId);
            Department d = Department.builder().name("General").code("GEN")
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            return deptRepo.save(d);
        });

        // Get or create default category
        Category cat = catRepo.findById(categoryId).orElseGet(() -> {
            log.warn("Category {} not found, creating default", categoryId);
            Category c = Category.builder().name("General").code("GENERAL")
                    .isActive(true).createdAt(LocalDateTime.now()).build();
            return catRepo.save(c);
        });

        // FX conversion (safe — defaults to 1.0 if no rates cached)
        BigDecimal amountUsd = amount;
        BigDecimal exchangeRate = BigDecimal.ONE;
        try {
            amountUsd = currencyService.convert(amount, currency, "USD");
            exchangeRate = currencyService.getRateToBase(currency);
        } catch (Exception ex) {
            log.warn("Currency conversion failed, using 1:1 rate: {}", ex.getMessage());
        }

        String refCode = generateReferenceCode();

        Expense expense = Expense.builder()
                .referenceCode(refCode)
                .submittedBy(user)
                .department(dept)
                .category(cat)
                .title(title)
                .description(description)
                .vendor(vendor)
                .amount(amount)
                .currency(currency)
                .amountUsd(amountUsd)
                .exchangeRate(exchangeRate)
                .expenseDate(expenseDate)
                .receiptUrl(receiptUrl)
                .status(ExpenseStatus.PENDING)
                .policyStatus(Expense.PolicyStatus.CLEAN)
                .isRecurring(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        expense = expenseRepo.save(expense);
        log.info("Expense {} SAVED to DB (id={})", refCode, expense.getId());

        // Run policy engine (non-blocking)
        try {
            List<PolicyViolation> violations = policyService.evaluateExpense(expense);
            if (!violations.isEmpty()) {
                notificationService.notifyPolicyViolation(user, expense, violations);
            }
        } catch (Exception ex) {
            log.warn("Policy evaluation failed (expense still saved): {}", ex.getMessage());
        }

        // Notify manager for approval (non-blocking)
        try {
            if (expense.getPolicyStatus() != Expense.PolicyStatus.REJECTED) {
                notificationService.notifyManagersForApproval(expense);
            }
        } catch (Exception ex) {
            log.warn("Manager notification failed (expense still saved): {}", ex.getMessage());
        }

        // Audit (non-blocking)
        try {
            writeAudit("EXPENSE", expense.getId(), "CREATE", null,
                    Map.of("ref", refCode, "amount", amount, "currency", currency), user);
        } catch (Exception ex) {
            log.warn("Audit log failed (expense still saved): {}", ex.getMessage());
        }

        log.info("Expense {} fully processed for user {}", refCode, user.getEmail());
        return expense;
    }

    /** Get all expenses for a specific user. */
    @Transactional(readOnly = true)
    public List<Expense> getUserExpenses(Long userId) {
        return expenseRepo.findBySubmittedBy_Id(userId, Pageable.unpaged()).getContent();
    }

    /** Get all expenses (admin/manager view). */
    @Transactional(readOnly = true)
    public List<Expense> getAllExpenses() {
        return expenseRepo.findAll();
    }

    /** Approve an expense (manager/finance action). */
    public Expense approveExpense(Long expenseId, Long approverId, String remarks) {
        Expense expense = expenseRepo.findById(expenseId).orElseThrow();
        User approver = userRepo.findById(approverId).orElseThrow();

        // Record approval
        ExpenseApproval approval = ExpenseApproval.builder()
                .expense(expense)
                .approver(approver)
                .level((short) 1)
                .action(ExpenseApproval.ApprovalAction.APPROVED)
                .remarks(remarks)
                .actionedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        approvalRepo.save(approval);

        ExpenseStatus old = expense.getStatus();
        expense.setStatus(ExpenseStatus.APPROVED);
        expense.setUpdatedAt(LocalDateTime.now());
        expenseRepo.save(expense);

        // Notify submitter
        notificationService.notifyExpenseApproved(expense.getSubmittedBy(), expense);

        // Trigger ERP sync
        erpService.syncExpense(expenseId);

        writeAudit("EXPENSE", expenseId, "APPROVE",
                Map.of("status", old.name()),
                Map.of("status", "APPROVED", "approvedBy", approver.getEmail()),
                approver);

        return expense;
    }

    /** Reject an expense. */
    public Expense rejectExpense(Long expenseId, Long approverId, String reason) {
        Expense expense = expenseRepo.findById(expenseId).orElseThrow();
        User approver = userRepo.findById(approverId).orElseThrow();

        ExpenseApproval rejection = ExpenseApproval.builder()
                .expense(expense)
                .approver(approver)
                .level((short) 1)
                .action(ExpenseApproval.ApprovalAction.REJECTED)
                .remarks(reason)
                .actionedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        approvalRepo.save(rejection);

        expense.setStatus(ExpenseStatus.REJECTED);
        expense.setUpdatedAt(LocalDateTime.now());
        expenseRepo.save(expense);

        notificationService.notifyExpenseRejected(expense.getSubmittedBy(), expense, reason);

        writeAudit("EXPENSE", expenseId, "REJECT", null,
                Map.of("status", "REJECTED", "reason", reason), approver);

        return expense;
    }

    /** Dashboard analytics summary. */
    @Transactional(readOnly = true)
    public Map<String, Object> getAnalyticsSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalApproved", expenseRepo.sumTotalApproved());
        summary.put("totalPending", expenseRepo.sumTotalPending());
        summary.put("pendingCount", expenseRepo.findByStatus(ExpenseStatus.PENDING).size());
        return summary;
    }

    public Page<Expense> getAllExpenses(Pageable pageable) {
        return expenseRepo.findAll(pageable);
    }

    public List<Expense> getPendingExpenses() {
        return expenseRepo.findByStatus(ExpenseStatus.PENDING);
    }

    public Expense getById(Long id) {
        return expenseRepo.findById(id).orElseThrow();
    }

    // ── Audit helper ─────────────────────────────────────────
    private void writeAudit(String entityType, Long entityId, String action,
            Map<String, Object> oldVal, Map<String, Object> newVal, User performer) {
        AuditLog auditEntry = AuditLog.builder()
                .entityType(entityType).entityId(entityId).action(action)
                .oldValue(oldVal).newValue(newVal)
                .performedBy(performer)
                .createdAt(LocalDateTime.now())
                .build();
        auditRepo.save(auditEntry);
    }

    private String generateReferenceCode() {
        String date = LocalDate.now().toString().replace("-", "");
        String uuid = java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return String.format("EXP-%s-%s", date, uuid);
    }
}
