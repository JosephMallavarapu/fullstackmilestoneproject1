package com.trackwise.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "policy_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String name;
    @Lob
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleAction action;
    @Column(precision = 15, scale = 2)
    private BigDecimal threshold;
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum RuleType {
        AMOUNT_LIMIT, DAILY_CAP, RECEIPT_REQUIRED, DUPLICATE_DETECTION, WEEKEND_BLOCK, CATEGORY_BUDGET, CUSTOM
    }

    public enum Severity {
        INFO, WARNING, CRITICAL
    }

    public enum RuleAction {
        FLAG, AUTO_REJECT
    }
}
