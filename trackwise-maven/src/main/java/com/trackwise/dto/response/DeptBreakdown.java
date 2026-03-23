package com.trackwise.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeptBreakdown {
    private String department;
    private BigDecimal total;
    private BigDecimal budget;
    private Double utilisationPercent;
}
