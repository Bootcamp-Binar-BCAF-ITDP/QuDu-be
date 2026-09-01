package com.delvin.loan.dto.response.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
        LocalDate from,
        LocalDate to,
        Summary summary,
        List<TimeSeriesPoint> applicationsOverTime,
        List<StatusSlice> byStatus,
        long byStatusTotal
) {

    public record MetricCard(
            BigDecimal value,
            Double changePercent,
            String direction
    ) {}

    public record Summary(
            MetricCard totalApplications,
            MetricCard pending,
            MetricCard approved,
            MetricCard rejected,
            MetricCard totalDisbursed
    ) {}

    public record TimeSeriesPoint(
            LocalDate date,
            long all,
            long approved,
            long rejected,
            long pending
    ) {}

    public record StatusSlice(
            String key,
            String label,
            long count,
            double percentage
    ) {}
}