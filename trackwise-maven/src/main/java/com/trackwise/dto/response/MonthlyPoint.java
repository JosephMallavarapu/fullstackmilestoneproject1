package com.trackwise.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyPoint {
    private String period;
    private Long txCount;
    private BigDecimal total;
}
