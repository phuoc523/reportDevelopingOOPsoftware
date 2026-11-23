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

    @Test
    void revenueBreakdownAggregatesByTenantMachineAndProduct() {
        var date = LocalDate.of(2026, 11, 13);
        var tenantRow = mock(TransactionRepository.RevenueDimensionProjection.class);
        when(tenantRow.getDimensionKey()).thenReturn("12");
        when(tenantRow.getTransactionCount()).thenReturn(3L);
        when(tenantRow.getRevenue()).thenReturn(new java.math.BigDecimal("75000"));
        var machineRow = mock(TransactionRepository.RevenueDimensionProjection.class);
        when(machineRow.getDimensionKey()).thenReturn("44");
        when(machineRow.getTransactionCount()).thenReturn(3L);
        when(machineRow.getRevenue()).thenReturn(new java.math.BigDecimal("75000"));
        var productRow = mock(TransactionRepository.RevenueDimensionProjection.class);
        when(productRow.getDimensionKey()).thenReturn("91");
        when(productRow.getTransactionCount()).thenReturn(3L);
        when(productRow.getRevenue()).thenReturn(new java.math.BigDecimal("75000"));

        when(repository.findRevenueByTenant(date.atStartOfDay(), date.plusDays(1).atStartOfDay(), "12", null, null))
                .thenReturn(java.util.List.of(tenantRow));
        when(repository.findRevenueByMachine(date.atStartOfDay(), date.plusDays(1).atStartOfDay(), "12", null, null))
                .thenReturn(java.util.List.of(machineRow));
        when(repository.findRevenueByProduct(date.atStartOfDay(), date.plusDays(1).atStartOfDay(), "12", null, null))
                .thenReturn(java.util.List.of(productRow));

        var result = service.revenueBreakdown(date, date, "12", null, null);
        assertEquals(3L, result.totalPaidTransactions());
        assertEquals(new java.math.BigDecimal("75000"), result.totalRevenue());
        assertEquals("12", result.byTenant().get(0).key());
        assertEquals("44", result.byMachine().get(0).key());
        assertEquals("91", result.byProduct().get(0).key());
    }
}
