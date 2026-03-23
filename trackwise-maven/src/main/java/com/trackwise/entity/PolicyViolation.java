package com.trackwise.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "policy_violations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyViolation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private PolicyRule rule;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ViolationResult result;
    @Lob
    private String detail;
    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

    public enum ViolationResult {
        PASS, FLAGGED, REJECTED
    }
}
