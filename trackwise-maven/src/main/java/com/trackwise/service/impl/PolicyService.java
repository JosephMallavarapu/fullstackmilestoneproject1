package com.trackwise.service.impl;

import com.trackwise.entity.*;
import com.trackwise.enums.ExpenseStatus;
import com.trackwise.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

// ═══════════════════════════════════════════════════════════
//  PolicyService — evaluates all active rules on an Expense
//  and persists PolicyViolation records + updates policy_status
// ═══════════════════════════════════════════════════════════
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PolicyService {

    private final PolicyRuleRepository policyRuleRepo;
    private final PolicyViolationRepository policyViolationRepo;
    private final ExpenseRepository expenseRepo;

    /**
     * Evaluate all active policy rules for an expense. Updates
     * expense.policyStatus.
     */
    public List<PolicyViolation> evaluateExpense(Expense expense) {
        List<PolicyRule> activeRules = policyRuleRepo.findByIsActiveTrueOrderByCreatedAtDesc();
        List<PolicyViolation> results = new ArrayList<>();
        boolean autoRejected = false;
        boolean flagged = false;

        for (PolicyRule rule : activeRules) {
            PolicyViolation.ViolationResult outcome = applyRule(rule, expense);

            if (outcome != PolicyViolation.ViolationResult.PASS) {
                PolicyViolation violation = PolicyViolation.builder()
                        .expense(expense)
                        .rule(rule)
                        .result(outcome)
                        .detail(buildDetail(rule, expense))
                        .evaluatedAt(LocalDateTime.now())
                        .build();
                policyViolationRepo.save(violation);
                results.add(violation);

                if (outcome == PolicyViolation.ViolationResult.REJECTED)
                    autoRejected = true;
                else if (outcome == PolicyViolation.ViolationResult.FLAGGED)
                    flagged = true;
            }
        }

        // Update expense policy status
        if (autoRejected) {
            expense.setPolicyStatus(Expense.PolicyStatus.REJECTED);
            expense.setStatus(ExpenseStatus.REJECTED);
        } else if (flagged) {
            expense.setPolicyStatus(Expense.PolicyStatus.FLAGGED);
        } else {
            expense.setPolicyStatus(Expense.PolicyStatus.CLEAN);
        }
        expenseRepo.save(expense);
        log.info("PolicyEngine: expense {} evaluated — {} violations", expense.getReferenceCode(), results.size());
        return results;
    }

    private PolicyViolation.ViolationResult applyRule(PolicyRule rule, Expense expense) {
        return switch (rule.getRuleType()) {
            case AMOUNT_LIMIT -> checkAmountLimit(rule, expense);
            case DAILY_CAP -> checkDailyCap(rule, expense);
            case RECEIPT_REQUIRED -> checkReceiptRequired(rule, expense);
            case DUPLICATE_DETECTION -> checkDuplicate(rule, expense);
            case WEEKEND_BLOCK -> checkWeekend(expense);
            case CATEGORY_BUDGET -> checkCategoryBudget(expense);
            default -> PolicyViolation.ViolationResult.PASS;
        };
    }

    private PolicyViolation.ViolationResult checkAmountLimit(PolicyRule rule, Expense expense) {
        if (expense.getAmountUsd().compareTo(rule.getThreshold()) > 0) {
            return toResult(rule);
        }
        return PolicyViolation.ViolationResult.PASS;
    }

    private PolicyViolation.ViolationResult checkDailyCap(PolicyRule rule, Expense expense) {
        BigDecimal dailyTotal = expenseRepo.sumDailySpend(
                expense.getSubmittedBy().getId(), expense.getExpenseDate());
        if (dailyTotal.add(expense.getAmountUsd()).compareTo(rule.getThreshold()) > 0) {
            return toResult(rule);
        }
        return PolicyViolation.ViolationResult.PASS;
    }

    private PolicyViolation.ViolationResult checkReceiptRequired(PolicyRule rule, Expense expense) {
        if (expense.getAmountUsd().compareTo(rule.getThreshold()) > 0
                && (expense.getReceiptUrl() == null || expense.getReceiptUrl().isBlank())) {
            return toResult(rule);
        }
        return PolicyViolation.ViolationResult.PASS;
    }

    private PolicyViolation.ViolationResult checkDuplicate(PolicyRule rule, Expense expense) {
        int windowDays = rule.getThreshold() != null ? rule.getThreshold().intValue() : 7;
        LocalDate since = expense.getExpenseDate().minusDays(windowDays);
        if (expense.getVendor() != null) {
            long count = expenseRepo.countDuplicates(
                    expense.getSubmittedBy().getId(),
                    expense.getVendor(),
                    expense.getAmountUsd(),
                    since);
            if (count > 0)
                return toResult(rule);
        }
        return PolicyViolation.ViolationResult.PASS;
    }

    private PolicyViolation.ViolationResult checkWeekend(Expense expense) {
        DayOfWeek day = expense.getExpenseDate().getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return PolicyViolation.ViolationResult.FLAGGED;
        }
        return PolicyViolation.ViolationResult.PASS;
    }

    private PolicyViolation.ViolationResult checkCategoryBudget(Expense expense) {
        LocalDate date = expense.getExpenseDate();
        BigDecimal spent = expenseRepo.sumByCategoryDeptMonth(
                expense.getDepartment().getId(),
                expense.getCategory().getId(),
                date.getYear(),
                date.getMonthValue());
        BigDecimal limit = expense.getCategory().getDefaultLimit();
        if (limit != null && spent.add(expense.getAmountUsd()).compareTo(limit) > 0) {
            return PolicyViolation.ViolationResult.FLAGGED;
        }
        return PolicyViolation.ViolationResult.PASS;
    }

    private PolicyViolation.ViolationResult toResult(PolicyRule rule) {
        return rule.getAction() == PolicyRule.RuleAction.AUTO_REJECT
                ? PolicyViolation.ViolationResult.REJECTED
                : PolicyViolation.ViolationResult.FLAGGED;
    }

    private String buildDetail(PolicyRule rule, Expense expense) {
        return String.format("Rule '%s' triggered for expense %s — amount USD %.2f",
                rule.getName(), expense.getReferenceCode(), expense.getAmountUsd());
    }

    /** Toggle rule on/off. Returns updated rule. */
    public PolicyRule toggleRule(Long ruleId) {
        PolicyRule rule = policyRuleRepo.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Policy rule not found: " + ruleId));
        rule.setIsActive(!rule.getIsActive());
        rule.setUpdatedAt(LocalDateTime.now());
        return policyRuleRepo.save(rule);
    }

    public List<PolicyRule> getAllRules() {
        return policyRuleRepo.findAll();
    }

    public List<PolicyViolation> getRecentViolations(org.springframework.data.domain.Pageable pageable) {
        return policyViolationRepo.findRecentViolations(pageable);
    }
}
