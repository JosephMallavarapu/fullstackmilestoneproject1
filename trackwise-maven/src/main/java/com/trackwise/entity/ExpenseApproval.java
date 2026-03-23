package com.trackwise.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "expense_approvals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseApproval {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "approvals" })
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id", nullable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "passwordHash" })
    private User approver;

    @Column(nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Short level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalAction action;

    @Lob
    private String remarks;

    @Column(name = "actioned_at")
    private LocalDateTime actionedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum ApprovalAction {
        PENDING, APPROVED, REJECTED, ESCALATED
    }
}
