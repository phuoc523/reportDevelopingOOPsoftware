
package com.scentstation.reporting.service;

import com.scentstation.reporting.dto.TransactionSummaryResponse;
import com.scentstation.reporting.enumtype.ReportPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionReportServiceTest {

    private TransactionReportService transactionReportService;

    @BeforeEach
    void setUp() {
        transactionReportService = new TransactionReportService();
    }

    @Test
    void getSummaryForDayShouldReturnToday() {
        LocalDate today = LocalDate.now();

        TransactionSummaryResponse response =
                transactionReportService.getSummary(
                        ReportPeriod.DAY, null, null
                );

        assertEquals(today, response.startDate());
        assertEquals(today, response.endDate());
        assertEquals("day", response.period());
    }

    @Test
    void getSummaryForWeekShouldReturnMondayToSunday() {
        LocalDate today = LocalDate.now();

        LocalDate expectedStart = today.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );
        LocalDate expectedEnd = expectedStart.plusDays(6);

        TransactionSummaryResponse response =
                transactionReportService.getSummary(
                        ReportPeriod.WEEK, null, null
                );

        assertEquals(expectedStart, response.startDate());
        assertEquals(expectedEnd, response.endDate());
        assertEquals("week", response.period());
    }

    @Test
    void getSummaryForMonthShouldReturnFirstAndLastDay() {
        LocalDate today = LocalDate.now();

        LocalDate expectedStart = today.withDayOfMonth(1);
        LocalDate expectedEnd = today.with(
                TemporalAdjusters.lastDayOfMonth()
        );

        TransactionSummaryResponse response =
                transactionReportService.getSummary(
                        ReportPeriod.MONTH, null, null
                );

        assertEquals(expectedStart, response.startDate());
        assertEquals(expectedEnd, response.endDate());
        assertEquals("month", response.period());
    }

    @Test
    void getSummaryForCustomPeriodShouldPreserveRequestedDates() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 15);

        TransactionSummaryResponse response =
                transactionReportService.getSummary(
                        ReportPeriod.CUSTOM, start, end
                );

        assertEquals(start, response.startDate());
        assertEquals(end, response.endDate());
        assertEquals("custom", response.period());
    }
}

