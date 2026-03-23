package com.trackwise.dto.response;

import com.trackwise.enums.ExpenseStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseResponse {
    private Long id;
    private String referenceNo;
    private String description;
    private BigDecimal amount;
    private BigDecimal amountUsd;
    private String currency;
    private String categoryName;
    private String categoryColor;
    private String departmentName;
    private String submittedByName;
    private String submittedByEmail;
    private LocalDate expenseDate;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
    private ExpenseStatus status;
    private String receiptUrl;
    private String notes;
    private Boolean isRecurring;
    private Set<String> tags;
    private List<ApprovalResponse> approvals;
}
