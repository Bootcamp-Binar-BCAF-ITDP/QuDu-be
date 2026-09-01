package com.delvin.loan.service;

import com.delvin.loan.common.DashboardPeriod;
import com.delvin.loan.common.DateRange;
import com.delvin.loan.common.StatusGroup;
import com.delvin.loan.dto.response.dashboard.*;
import com.delvin.loan.repository.LoanApplicationRepository;
import com.delvin.loan.repository.LoanApplicationRepository.DailyStatusCount;
import com.delvin.loan.repository.LoanApplicationRepository.StatusCount;
import com.delvin.loan.repository.LoanDisbursementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class DashboardService {

    private final LoanApplicationRepository applicationRepository;
    private final LoanDisbursementRepository disbursementRepository;

    public DashboardService(
            LoanApplicationRepository applicationRepository,
            LoanDisbursementRepository disbursementRepository
    ) {
        this.applicationRepository = applicationRepository;
        this.disbursementRepository = disbursementRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(DashboardPeriod period) {
        LocalDate today = LocalDate.now();
        DateRange current = resolve(period, today);
        DateRange previous = previous(period, current);

        Map<StatusGroup, Long> now = group(
                applicationRepository.countByStatusBetween(current.from(), current.to()));
        Map<StatusGroup, Long> before = group(
                applicationRepository.countByStatusBetween(previous.from(), previous.to()));

        BigDecimal disbursedNow = nullSafe(
                disbursementRepository.sumDisbursedBetween(current.from(), current.to()));
        BigDecimal disbursedBefore = nullSafe(
                disbursementRepository.sumDisbursedBetween(previous.from(), previous.to()));

        DashboardResponse.Summary summary = new DashboardResponse.Summary(
                card(total(now), total(before)),
                card(count(now, StatusGroup.PENDING), count(before, StatusGroup.PENDING)),
                card(count(now, StatusGroup.APPROVED), count(before, StatusGroup.APPROVED)),
                card(count(now, StatusGroup.REJECTED), count(before, StatusGroup.REJECTED)),
                card(disbursedNow, disbursedBefore)
        );

        long grandTotal = total(now).longValue();

        return new DashboardResponse(
                current.from(),
                current.to(),
                summary,
                timeSeries(current),
                slices(now, grandTotal),
                grandTotal
        );
    }

    private List<DashboardResponse.TimeSeriesPoint> timeSeries(DateRange range) {
        Map<LocalDate, EnumMap<StatusGroup, Long>> byDay = new HashMap<>();

        for (DailyStatusCount row : applicationRepository
                .countByDayAndStatusBetween(range.from(), range.to())) {
            byDay.computeIfAbsent(row.getDay(), d -> new EnumMap<>(StatusGroup.class))
                    .merge(StatusGroup.of(row.getStatus()), row.getTotal(), Long::sum);
        }

        List<DashboardResponse.TimeSeriesPoint> points = new ArrayList<>();
        long all = 0, approved = 0, rejected = 0, pending = 0;

        // Walk every day so gaps render as a flat segment rather than vanishing.
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
            EnumMap<StatusGroup, Long> counts =
                    byDay.getOrDefault(day, new EnumMap<>(StatusGroup.class));

            approved += counts.getOrDefault(StatusGroup.APPROVED, 0L);
            rejected += counts.getOrDefault(StatusGroup.REJECTED, 0L);
            pending += counts.getOrDefault(StatusGroup.PENDING, 0L);
            all += counts.values().stream().mapToLong(Long::longValue).sum();

            points.add(new DashboardResponse.TimeSeriesPoint(day, all, approved, rejected, pending));
        }

        return points;
    }

    // ---- donut ----

    private List<DashboardResponse.StatusSlice> slices(Map<StatusGroup, Long> counts, long total) {
        return List.of(
                slice("pending", "Pending", counts.getOrDefault(StatusGroup.PENDING, 0L), total),
                slice("approved", "Approved", counts.getOrDefault(StatusGroup.APPROVED, 0L), total),
                slice("rejected", "Rejected", counts.getOrDefault(StatusGroup.REJECTED, 0L), total)
        );
    }

    private DashboardResponse.StatusSlice slice(String key, String label, long count, long total) {
        double percentage = total == 0 ? 0 : Math.round(count * 1000.0 / total) / 10.0;
        return new DashboardResponse.StatusSlice(key, label, count, percentage);
    }

    private DashboardResponse.MetricCard card(BigDecimal value, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return new DashboardResponse.MetricCard(value, null, "FLAT");
        }

        BigDecimal change = value.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);

        String direction = change.signum() > 0 ? "UP" : change.signum() < 0 ? "DOWN" : "FLAT";
        return new DashboardResponse.MetricCard(value, change.doubleValue(), direction);
    }

    private Map<StatusGroup, Long> group(List<StatusCount> rows) {
        EnumMap<StatusGroup, Long> grouped = new EnumMap<>(StatusGroup.class);
        for (StatusCount row : rows) {
            grouped.merge(StatusGroup.of(row.getStatus()), row.getTotal(), Long::sum);
        }
        return grouped;
    }

    private BigDecimal count(Map<StatusGroup, Long> counts, StatusGroup group) {
        return BigDecimal.valueOf(counts.getOrDefault(group, 0L));
    }

    private BigDecimal total(Map<StatusGroup, Long> counts) {
        return BigDecimal.valueOf(
                counts.values().stream().mapToLong(Long::longValue).sum());
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private DateRange resolve(DashboardPeriod period, LocalDate today) {
        return switch (period) {
            case THIS_MONTH -> new DateRange(today.withDayOfMonth(1), today);
            case LAST_MONTH -> {
                LocalDate start = today.minusMonths(1).withDayOfMonth(1);
                yield new DateRange(start, start.withDayOfMonth(start.lengthOfMonth()));
            }
            case LAST_7_DAYS -> new DateRange(today.minusDays(6), today);
            case LAST_30_DAYS -> new DateRange(today.minusDays(29), today);
            case THIS_YEAR -> new DateRange(today.withDayOfYear(1), today);
        };
    }

    private DateRange previous(DashboardPeriod period, DateRange current) {
        return switch (period) {
            case THIS_MONTH, LAST_MONTH ->
                    new DateRange(current.from().minusMonths(1), current.to().minusMonths(1));
            case THIS_YEAR ->
                    new DateRange(current.from().minusYears(1), current.to().minusYears(1));
            case LAST_7_DAYS, LAST_30_DAYS -> {
                long days = ChronoUnit.DAYS.between(current.from(), current.to()) + 1;
                yield new DateRange(current.from().minusDays(days), current.from().minusDays(1));
            }
        };
    }
}