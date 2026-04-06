package com.smartfinvo.modules.analytics.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Time-series spending data grouped by day, week, or month.")
public class SpendingTrendResponse {

    @Schema(description = "Ordered list of spending data points, earliest first")
    private List<TrendPoint> trends;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Spending total for a single time bucket.")
    public static class TrendPoint {

        // ISO-8601 date string representing the start of the bucket
        // day   → "2026-04-05"
        // week  → "2026-03-30"  (Monday of that week)
        // month → "2026-04-01"  (first day of the month)
        @Schema(description = "Start date of this time bucket (YYYY-MM-DD)", example = "2026-04-01")
        private String date;

        @Schema(description = "Total amount spent in this bucket", example = "87.40")
        private BigDecimal total;

        @Schema(description = "Number of expense records in this bucket", example = "6")
        private Long count;
    }
}
