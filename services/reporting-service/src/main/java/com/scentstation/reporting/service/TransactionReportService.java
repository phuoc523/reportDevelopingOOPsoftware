
package com.scentstation.reporting.service;

import com.scentstation.reporting.dto.TransactionSummaryResponse;
import com.scentstation.reporting.enumtype.ReportPeriod;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Service
public class TransactionReportService {

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

        // Demo data only; not yet connected to the database.
        return new TransactionSummaryResponse(
                100,
                75,
                5,
                8,
                7,
                5,
                new BigDecimal("1500000.00"),
                period.name().toLowerCase(),
                startDate,
                endDate
        );
    }
}
