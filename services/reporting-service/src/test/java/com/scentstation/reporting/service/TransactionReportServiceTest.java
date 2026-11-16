
package com.scentstation.reporting.service;

import com.scentstation.reporting.dto.TransactionSummaryResponse;
import com.scentstation.reporting.enumtype.ReportPeriod;
import com.scentstation.reporting.repository.TransactionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TransactionReportServiceTest {

    private TransactionRepository transactionRepository;
    private TransactionReportService transactionReportService;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);

        when(transactionRepository
                .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)
                )).thenReturn(1L);

        when(transactionRepository
                .countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndStatus(
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        anyString()
                )).thenAnswer(invocation -> {
                    String status = invocation.getArgument(2);

                    return "PAID".equals(status) ? 1L : 0L;
                });

        when(transactionRepository.sumPaidAmountBetween(
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(new BigDecimal("250000.00"));

        transactionReportService =
                new TransactionReportService(transactionRepository);
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

    @Test
    void getSummaryShouldReturnDatabaseStatistics() {
        TransactionSummaryResponse response =
                transactionReportService.getSummary(
                        ReportPeriod.DAY, null, null
                );

        assertEquals(1L, response.totalTransactions());
        assertEquals(1L, response.successfulTransactions());
        assertEquals(0L, response.pendingTransactions());
        assertEquals(0L, response.failedTransactions());
        assertEquals(0L, response.expiredTransactions());
        assertEquals(0L, response.refundedTransactions());
        assertEquals(
                new BigDecimal("250000.00"),
                response.totalRevenue()
        );
    }
}
