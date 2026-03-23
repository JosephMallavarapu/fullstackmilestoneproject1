package com.trackwise.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSummaryResponse {
    private BigDecimal totalSpend;
    private BigDecimal approvedSpend;
    private BigDecimal pendingSpend;
    private Long totalCount;
    private Long pendingCount;
    private Long approvedCount;
    private int activeCategoryCount;
    private List<CategoryBreakdown> byCategory;
    private List<DeptBreakdown> byDepartment;
    private List<MonthlyPoint> monthlyTrend;
    private List<StatusCount> byStatus;
    private List<TopSpender> topSpenders;
}
