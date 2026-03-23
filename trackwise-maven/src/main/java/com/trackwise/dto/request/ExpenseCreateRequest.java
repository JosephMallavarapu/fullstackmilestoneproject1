package com.trackwise.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseCreateRequest {
    @NotNull
    private Long userId;
    @NotNull
    private Long departmentId;
    @NotNull
    private Long categoryId;
    @NotBlank
    private String title;
    private String description;
    private String vendor;
    @NotNull
    @Positive
    private BigDecimal amount;
    @NotBlank
    @Size(min = 3, max = 3)
    private String currency;
    @NotNull
    private LocalDate expenseDate;
    private String receiptUrl;
    private Long ocrScanId;
}
