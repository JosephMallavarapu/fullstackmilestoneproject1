package com.trackwise.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryBreakdown {
    private String category;
    private BigDecimal total;
    private Double percentage;
}
