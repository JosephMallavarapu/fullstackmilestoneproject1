package com.trackwise.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopSpender {
    private Long userId;
    private String fullName;
    private Long expenseCount;
    private BigDecimal totalSpent;
}
