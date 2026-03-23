package com.trackwise.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.trackwise.enums.ExpenseStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "reference_code", nullable = false, unique = true, length = 30)
    private String referenceCode;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by", nullable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "passwordHash" })
    private User submittedBy;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private Department department;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private Category category;
    @Column(nullable = false)
    private String title;
    @Lob
    private String description;
    @Column(length = 255)
    private String vendor;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "amount_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountUsd;
    @Column(name = "exchange_rate", precision = 10, scale = 6)
    private BigDecimal exchangeRate;
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;
    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ocr_scan_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private OcrScan ocrScan;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ExpenseStatus status = ExpenseStatus.DRAFT;
    @Enumerated(EnumType.STRING)
    @Column(name = "policy_status", nullable = false)
    @Builder.Default
    private PolicyStatus policyStatus = PolicyStatus.CLEAN;
    @Column(name = "is_recurring")
    @Builder.Default
    private Boolean isRecurring = false;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<ExpenseApproval> approvals;

    public enum PolicyStatus {
        CLEAN, FLAGGED, REJECTED
    }
}
