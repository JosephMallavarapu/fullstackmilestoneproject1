package com.trackwise.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetUtilisationResponse {
    private String department;
    private String category;
    private Short fiscalYear;
    private BigDecimal budgetAmount;
    private BigDecimal spentAmount;
    private BigDecimal remaining;
    private Double utilisationPercent;
    private Boolean overBudget;
}
