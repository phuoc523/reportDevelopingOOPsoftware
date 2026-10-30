package com.scentstation.reporting.service;

import com.scentstation.reporting.dto.TransactionSummaryResponse;
import com.scentstation.reporting.enumtype.ReportPeriod;
import com.scentstation.reporting.repository.TransactionRepository;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

@Service
public class TransactionReportService {

    private final TransactionRepository transactionRepository;

    public TransactionReportService(
            TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public TransactionSummaryResponse getSummary(
            ReportPeriod period,
            LocalDate requestedStartDate,
            LocalDate requestedEndDate) {

        LocalDate startDate;
        LocalDate endDate;

        LocalDate today = LocalDate.now();

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

        return new TransactionSummaryResponse(
                totalTransactions,
                successful,
                pending,
                failed,
                expired,
                refunded,
                revenue,
                period.name().toLowerCase(),
                startDate,
                endDate
        );
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
}