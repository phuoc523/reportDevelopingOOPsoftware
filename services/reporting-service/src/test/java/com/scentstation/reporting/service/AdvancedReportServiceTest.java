package com.scentstation.reporting.service;

import com.scentstation.reporting.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvancedReportServiceTest {
    private final TransactionRepository repository = mock(TransactionRepository.class);
    private final AdvancedReportService service = new AdvancedReportService(repository);

    @Test
    void invalidDateRangesNeverQueryFinancialData() {
        var start = LocalDate.of(2026, 11, 13);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.revenueDetails(start, start.minusDays(1))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.refundSummary(null, start)).getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void emptyStatusReportHasZeroRatesRatherThanNaN() {
        var date = LocalDate.of(2026, 11, 13);
        var summary = service.statusSummary(date, date);
        assertEquals(0, summary.totalTransactions());
        assertEquals(0, summary.paidRate());
        assertEquals(0, summary.refundedRate());
    }

    @Test
    void dailyRevenueIncludesTheEntireEndDate() {
        var date = LocalDate.of(2026, 11, 13);
        service.revenueDetails(date, date);
        verify(repository).findDailyRevenue(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }
}
