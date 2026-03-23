package com.trackwise.policy;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * ══════════════════════════════════════════════════════════════
 *  TrackWise — Policy Engine
 *  Industry Feature: Auto-reject / auto-flag rule violations
 *
 *  How it works:
 *  1. PolicyRule defines a rule (threshold, type, action)
 *  2. PolicyEngine.evaluate() runs ALL active rules against an expense
 *  3. Returns a PolicyResult with PASS / FLAGGED / REJECTED + reason
 *  4. Result is stored in the audit_log table
 *  5. If REJECTED, expense never reaches PENDING status
 * ══════════════════════════════════════════════════════════════
 */

// ── Rule severity ──────────────────────────────────────────────
enum PolicySeverity { INFO, WARNING, CRITICAL }

// ── What happens when rule triggers ───────────────────────────
enum PolicyAction   { FLAG, REJECT, NOTIFY }

// ── Outcome of running rules ───────────────────────────────────
enum PolicyDecision { PASS, FLAGGED, REJECTED }

// ─────────────────────────────────────────────────────────────
// PolicyRule — a configurable rule (stored in DB in production)
// ─────────────────────────────────────────────────────────────
@Data @Builder @NoArgsConstructor @AllArgsConstructor
class PolicyRule {
    private Long           id;
    private String         name;
    private String         description;
    private PolicySeverity severity;
    private PolicyAction   action;
    private boolean        active;
    private BigDecimal     thresholdAmount;   // for amount-based rules
    private String         ruleType;          // AMOUNT_LIMIT | DAILY_CAP | WEEKEND | RECEIPT_REQUIRED | DUPLICATE | BUDGET_EXCEEDED
}

// ─────────────────────────────────────────────────────────────
// PolicyViolation — details of a single rule breach
// ─────────────────────────────────────────────────────────────
@Data @Builder @NoArgsConstructor @AllArgsConstructor
class PolicyViolation {
    private String         ruleName;
    private String         message;
    private PolicySeverity severity;
    private PolicyAction   recommendedAction;
}

// ─────────────────────────────────────────────────────────────
// PolicyResult — full outcome for an expense evaluation
// ─────────────────────────────────────────────────────────────
@Data @Builder @NoArgsConstructor @AllArgsConstructor
class PolicyResult {
    private PolicyDecision      decision;       // PASS | FLAGGED | REJECTED
    private List<PolicyViolation> violations;
    private String              summary;

    public boolean isRejected() { return decision == PolicyDecision.REJECTED; }
    public boolean isFlagged()  { return decision == PolicyDecision.FLAGGED; }
    public boolean isPassed()   { return decision == PolicyDecision.PASS; }
}

// ─────────────────────────────────────────────────────────────
// PolicyEvaluationContext — data passed to each rule check
// ─────────────────────────────────────────────────────────────
@Data @Builder @NoArgsConstructor @AllArgsConstructor
class PolicyEvaluationContext {
    private BigDecimal  amount;
    private String      currency;
    private String      categoryName;
    private String      departmentName;
    private LocalDate   expenseDate;
    private Long        submittedByUserId;
    private boolean     hasReceipt;
    private String      vendorName;
    private String      description;
    // Injected by service for duplicate / daily cap checks
    private BigDecimal  userDailyTotalSoFar;
    private boolean     duplicateExists;
    private BigDecimal  categoryBudgetRemaining;
}

// ─────────────────────────────────────────────────────────────
// PolicyEngine — the core evaluator
// ─────────────────────────────────────────────────────────────
@Service
@Slf4j
public class PolicyEngine {

    /** Hardcoded defaults — in production these come from the `policy_rules` DB table */
    private static final List<PolicyRule> DEFAULT_RULES = List.of(

        PolicyRule.builder()
            .id(1L).name("Single Transaction Limit")
            .description("Expenses over $1,000 require pre-approval")
            .severity(PolicySeverity.CRITICAL).action(PolicyAction.FLAG)
            .active(true).thresholdAmount(new BigDecimal("1000"))
            .ruleType("AMOUNT_LIMIT").build(),

        PolicyRule.builder()
            .id(2L).name("Auto-Reject: Extreme Amounts")
            .description("Expenses over $10,000 are auto-rejected without pre-approval")
            .severity(PolicySeverity.CRITICAL).action(PolicyAction.REJECT)
            .active(true).thresholdAmount(new BigDecimal("10000"))
            .ruleType("AMOUNT_REJECT").build(),

        PolicyRule.builder()
            .id(3L).name("Daily Spend Cap")
            .description("Daily spend per user capped at $2,000")
            .severity(PolicySeverity.WARNING).action(PolicyAction.FLAG)
            .active(true).thresholdAmount(new BigDecimal("2000"))
            .ruleType("DAILY_CAP").build(),

        PolicyRule.builder()
            .id(4L).name("Receipt Required")
            .description("Receipt mandatory for amounts over $50")
            .severity(PolicySeverity.CRITICAL).action(PolicyAction.REJECT)
            .active(true).thresholdAmount(new BigDecimal("50"))
            .ruleType("RECEIPT_REQUIRED").build(),

        PolicyRule.builder()
            .id(5L).name("Weekend Submission Flag")
            .description("Weekend-dated expenses flagged for review")
            .severity(PolicySeverity.INFO).action(PolicyAction.FLAG)
            .active(true).ruleType("WEEKEND").build(),

        PolicyRule.builder()
            .id(6L).name("Duplicate Detection")
            .description("Same vendor + amount within 7 days flagged")
            .severity(PolicySeverity.WARNING).action(PolicyAction.FLAG)
            .active(true).ruleType("DUPLICATE").build(),

        PolicyRule.builder()
            .id(7L).name("Category Budget Exceeded")
            .description("Warn when category is over monthly budget")
            .severity(PolicySeverity.WARNING).action(PolicyAction.NOTIFY)
            .active(true).ruleType("BUDGET_EXCEEDED").build()
    );

    /**
     * Evaluate all active rules against an expense context.
     * Returns PolicyResult with decision + all violations.
     *
     * Usage in ExpenseService:
     *   PolicyResult result = policyEngine.evaluate(ctx);
     *   if (result.isRejected()) throw new PolicyRejectedException(result.getSummary());
     */
    public PolicyResult evaluate(PolicyEvaluationContext ctx) {
        List<PolicyViolation> violations = new ArrayList<>();
        PolicyDecision decision = PolicyDecision.PASS;

        for (PolicyRule rule : DEFAULT_RULES) {
            if (!rule.isActive()) continue;

            Optional<PolicyViolation> violation = checkRule(rule, ctx);
            if (violation.isPresent()) {
                violations.add(violation.get());
                log.info("Policy violation: rule='{}' action={} amount={}",
                    rule.getName(), rule.getAction(), ctx.getAmount());

                if (rule.getAction() == PolicyAction.REJECT) {
                    decision = PolicyDecision.REJECTED;
                } else if (rule.getAction() == PolicyAction.FLAG && decision != PolicyDecision.REJECTED) {
                    decision = PolicyDecision.FLAGGED;
                }
            }
        }

        String summary = buildSummary(decision, violations);
        log.info("Policy evaluation complete: decision={} violations={}", decision, violations.size());

        return PolicyResult.builder()
                .decision(decision)
                .violations(violations)
                .summary(summary)
                .build();
    }

    // ── Individual rule checkers ──────────────────────────────

    private Optional<PolicyViolation> checkRule(PolicyRule rule, PolicyEvaluationContext ctx) {
        return switch (rule.getRuleType()) {

            case "AMOUNT_LIMIT" -> {
                if (ctx.getAmount().compareTo(rule.getThresholdAmount()) > 0) {
                    yield Optional.of(PolicyViolation.builder()
                        .ruleName(rule.getName())
                        .message(String.format("Amount $%.2f exceeds $%.2f limit — flagged for manager review",
                                ctx.getAmount(), rule.getThresholdAmount()))
                        .severity(rule.getSeverity())
                        .recommendedAction(rule.getAction())
                        .build());
                }
                yield Optional.empty();
            }

            case "AMOUNT_REJECT" -> {
                if (ctx.getAmount().compareTo(rule.getThresholdAmount()) > 0) {
                    yield Optional.of(PolicyViolation.builder()
                        .ruleName(rule.getName())
                        .message(String.format("Amount $%.2f exceeds auto-reject threshold of $%.2f",
                                ctx.getAmount(), rule.getThresholdAmount()))
                        .severity(PolicySeverity.CRITICAL)
                        .recommendedAction(PolicyAction.REJECT)
                        .build());
                }
                yield Optional.empty();
            }

            case "DAILY_CAP" -> {
                if (ctx.getUserDailyTotalSoFar() != null) {
                    BigDecimal projected = ctx.getUserDailyTotalSoFar().add(ctx.getAmount());
                    if (projected.compareTo(rule.getThresholdAmount()) > 0) {
                        yield Optional.of(PolicyViolation.builder()
                            .ruleName(rule.getName())
                            .message(String.format("Projected daily total $%.2f exceeds cap of $%.2f",
                                    projected, rule.getThresholdAmount()))
                            .severity(rule.getSeverity())
                            .recommendedAction(rule.getAction())
                            .build());
                    }
                }
                yield Optional.empty();
            }

            case "RECEIPT_REQUIRED" -> {
                if (ctx.getAmount().compareTo(rule.getThresholdAmount()) > 0 && !ctx.isHasReceipt()) {
                    yield Optional.of(PolicyViolation.builder()
                        .ruleName(rule.getName())
                        .message(String.format("Receipt required for amounts over $%.2f", rule.getThresholdAmount()))
                        .severity(rule.getSeverity())
                        .recommendedAction(PolicyAction.REJECT)
                        .build());
                }
                yield Optional.empty();
            }

            case "WEEKEND" -> {
                DayOfWeek day = ctx.getExpenseDate().getDayOfWeek();
                if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                    yield Optional.of(PolicyViolation.builder()
                        .ruleName(rule.getName())
                        .message("Expense dated on a weekend (" + day + ") — flagged for review")
                        .severity(rule.getSeverity())
                        .recommendedAction(PolicyAction.FLAG)
                        .build());
                }
                yield Optional.empty();
            }

            case "DUPLICATE" -> {
                if (Boolean.TRUE.equals(ctx.getDuplicateExists())) {
                    yield Optional.of(PolicyViolation.builder()
                        .ruleName(rule.getName())
                        .message("Possible duplicate: same amount + vendor submitted within 7 days")
                        .severity(rule.getSeverity())
                        .recommendedAction(PolicyAction.FLAG)
                        .build());
                }
                yield Optional.empty();
            }

            case "BUDGET_EXCEEDED" -> {
                if (ctx.getCategoryBudgetRemaining() != null
                        && ctx.getCategoryBudgetRemaining().compareTo(BigDecimal.ZERO) < 0) {
                    yield Optional.of(PolicyViolation.builder()
                        .ruleName(rule.getName())
                        .message("Category budget exhausted — this expense exceeds remaining budget")
                        .severity(rule.getSeverity())
                        .recommendedAction(PolicyAction.NOTIFY)
                        .build());
                }
                yield Optional.empty();
            }

            default -> Optional.empty();
        };
    }

    private String buildSummary(PolicyDecision decision, List<PolicyViolation> violations) {
        if (violations.isEmpty()) return "All policy checks passed";
        String reasons = violations.stream()
                .map(PolicyViolation::getMessage)
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
        return decision + ": " + reasons;
    }
}
