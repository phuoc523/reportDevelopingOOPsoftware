package com.scentstation.reporting.service;

import com.scentstation.reporting.dto.TransactionSummaryResponse;
import com.scentstation.reporting.enumtype.ReportPeriod;
import com.scentstation.reporting.repository.TransactionRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

@Service
public class TransactionReportService {

    private final TransactionRepository transactionRepository;
    private final Clock clock;

    @Autowired
    public TransactionReportService(
            TransactionRepository transactionRepository,
            @Value("${reporting.zone-id:Asia/Ho_Chi_Minh}") String reportingZoneId) {
        this(transactionRepository, Clock.system(ZoneId.of(reportingZoneId)));
    }

    public TransactionReportService(TransactionRepository transactionRepository) {
        this(transactionRepository, Clock.systemDefaultZone());
    }

    TransactionReportService(TransactionRepository transactionRepository, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    public TransactionSummaryResponse getSummary(
            ReportPeriod period,
            LocalDate requestedStartDate,
            LocalDate requestedEndDate) {

        LocalDate startDate;
        LocalDate endDate;

        LocalDate today = LocalDate.now(clock);

        switch (period) {
            case DAY -> {
                startDate = today;
                endDate = today;
            }

            case WEEK -> {
                startDate = today.with(
                        TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
                );
                endDate = startDate.plusDays(6);
            }

            case MONTH -> {
                startDate = today.withDayOfMonth(1);
                endDate = today.with(
                        TemporalAdjusters.lastDayOfMonth()
                );
            }

            case CUSTOM -> {
                startDate = requestedStartDate;
                endDate = requestedEndDate;
            }

            default -> throw new IllegalArgumentException(
                    "Unsupported report period: " + period
            );
        }

        // Convert LocalDate to LocalDateTime.
        // End date is exclusive to include the entire end date.
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        // Count transactions.
        long totalTransactions =
                transactionRepository
                        .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                startDateTime,
                                endDateTime
                        );

        long successful =
                countByStatus(startDateTime, endDateTime, "PAID");

        long pending =
                countByStatus(startDateTime, endDateTime, "PENDING");

        long failed =
                countByStatus(startDateTime, endDateTime, "FAILED");

        long expired =
                countByStatus(startDateTime, endDateTime, "EXPIRED");

        long refunded =
                countByStatus(startDateTime, endDateTime, "REFUNDED");

        // Calculate revenue based on paidAt.
        BigDecimal revenue =
                transactionRepository.sumPaidAmountBetween(
                        startDateTime,
                        endDateTime
                );
        BigDecimal refundAmount = zeroIfNull(
                transactionRepository.sumRefundAmountBetween(startDateTime, endDateTime));
        revenue = zeroIfNull(revenue);

        return new TransactionSummaryResponse(
                totalTransactions,
                successful,
                pending,
                failed,
                expired,
                refunded,
                revenue,
                refundAmount,
                revenue.subtract(refundAmount),
                period.name().toLowerCase(),
                startDate,
                endDate
        );
    }

    public TransactionSummaryResponse getSummary(ReportPeriod period,
            LocalDate requestedStartDate, LocalDate requestedEndDate, String tenantId,
            String machineId, Long productId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate startDate;
        LocalDate endDate;
        switch (period) {
            case DAY -> { startDate = today; endDate = today; }
            case WEEK -> {
                startDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                endDate = startDate.plusDays(6);
            }
            case MONTH -> {
                startDate = today.withDayOfMonth(1);
                endDate = today.with(TemporalAdjusters.lastDayOfMonth());
            }
            case CUSTOM -> { startDate = requestedStartDate; endDate = requestedEndDate; }
            default -> throw new IllegalArgumentException("Unsupported report period: " + period);
        }
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        BigDecimal revenue = zeroIfNull(transactionRepository.sumPaidAmountScoped(
                start, end, tenantId, machineId, productId));
        BigDecimal refunds = zeroIfNull(transactionRepository.sumRefundAmountScoped(
                start, end, tenantId, machineId, productId));
        return new TransactionSummaryResponse(
                transactionRepository.countScoped(start, end, null, tenantId, machineId, productId),
                transactionRepository.countScoped(start, end, "PAID", tenantId, machineId, productId),
                transactionRepository.countScoped(start, end, "PENDING", tenantId, machineId, productId),
                transactionRepository.countScoped(start, end, "FAILED", tenantId, machineId, productId),
                transactionRepository.countScoped(start, end, "EXPIRED", tenantId, machineId, productId),
                transactionRepository.countScoped(start, end, "REFUNDED", tenantId, machineId, productId),
                revenue, refunds, revenue.subtract(refunds),
                period.name().toLowerCase(), startDate, endDate);
    }

    private long countByStatus(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String status) {

        return transactionRepository
                .countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndStatus(
                        startDate,
                        endDate,
                        status
                );
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
