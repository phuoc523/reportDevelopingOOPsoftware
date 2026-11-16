package com.scentstation.reporting.service;

import com.scentstation.reporting.client.OperationInventoryClient;
import com.scentstation.reporting.client.UpstreamUnavailableException;
import com.scentstation.reporting.dto.integration.*;
import com.scentstation.reporting.enumtype.ReportPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationReportServiceTest {
    @Mock OperationInventoryClient client;
    private OperationReportService service() {
        return new OperationReportService(client, ZoneId.of("UTC"),
                Clock.fixed(Instant.parse("2026-11-13T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
    }
    private InventoryMovementSnapshot movement(long id, String type, String before, String after, String date, String ref) {
        return new InventoryMovementSnapshot(id, 12L, type, new BigDecimal(before), new BigDecimal(after),
                ref, LocalDateTime.parse(date));
    }

    @Test
    void separatesConfirmedCustomerDiagnosticAndNonConsumptionMovements() {
        when(client.listMovements(12)).thenReturn(List.of(
                movement(1, "CUSTOMER_CONSUMPTION", "10", "9.75", "2026-11-13T01:00:00", "CMD1"),
                movement(2, "DIAGNOSTIC_CONSUMPTION", "9.75", "9.65", "2026-11-13T02:00:00", "CMD2"),
                movement(3, "ADJUSTMENT", "9.65", "8", "2026-11-13T02:00:00", "ADJ"),
                movement(4, "DISPOSE", "8", "0", "2026-11-13T02:00:00", "DISPOSE")));
        var result = service().getSummary(12, ReportPeriod.DAY);
        assertEquals(1, result.consumption().successfulCustomerCommands());
        assertEquals(1, result.consumption().successfulDiagnosticCommands());
        assertEquals(0, new BigDecimal("0.25").compareTo(result.consumption().customerConsumedQuantity()));
        assertEquals(0, new BigDecimal("0.10").compareTo(result.consumption().diagnosticConsumedQuantity()));
        assertNull(result.consumption().failedCommands());
        assertEquals("unsupported-missing-list-api", result.upstream().get("operation-failures"));
        assertEquals(result, service().getSummary(12, ReportPeriod.DAY));
    }

    @Test
    void utcMovementsAreFilteredByVietnamCalendarDayWithExclusiveEnd() {
        when(client.listMovements(12)).thenReturn(List.of(
                movement(1, "CUSTOMER_CONSUMPTION", "10", "9", "2026-11-12T16:59:59", "BEFORE"),
                movement(2, "CUSTOMER_CONSUMPTION", "9", "8", "2026-11-12T17:00:00", "START"),
                movement(3, "CUSTOMER_CONSUMPTION", "8", "7", "2026-11-13T16:59:59", "END"),
                movement(4, "CUSTOMER_CONSUMPTION", "7", "6", "2026-11-13T17:00:00", "AFTER")));
        var result = service().getSummary(12, ReportPeriod.DAY).consumption();
        assertEquals(2, result.successfulCustomerCommands());
        assertEquals(LocalDate.of(2026, 11, 13), result.startDate());
        assertEquals(result.startDate(), result.endDate());
    }

    @Test
    void duplicateCommandDoesNotInflateConsumption() {
        var first = movement(1, "CUSTOMER_CONSUMPTION", "10", "9", "2026-11-13T01:00:00", "CMD");
        var duplicate = movement(2, "CUSTOMER_CONSUMPTION", "10", "9", "2026-11-13T01:00:00", "CMD");
        when(client.listMovements(12)).thenReturn(List.of(first, duplicate));
        assertEquals(1, service().getSummary(12, ReportPeriod.DAY).consumption().successfulCustomerCommands());
    }

    @Test
    void conflictingCommandDataMakesConsumptionUnavailable() {
        when(client.listMovements(12)).thenReturn(List.of(
                movement(1, "CUSTOMER_CONSUMPTION", "10", "9", "2026-11-13T01:00:00", "CMD"),
                movement(2, "CUSTOMER_CONSUMPTION", "10", "8", "2026-11-13T01:00:00", "CMD")));
        var result = service().getSummary(12, ReportPeriod.DAY);
        assertNull(result.consumption());
        assertNotNull(result.maintenance());
        assertEquals("unavailable", result.upstream().get("operation-consumption"));
    }

    @Test
    void completedCancelledAndUnknownTicketsAreNotCountedAsActiveOrOverdue() {
        var past = LocalDateTime.of(2026, 11, 13, 4, 0);
        when(client.listMaintenanceTickets(12)).thenReturn(List.of(
                new MaintenanceTicketSnapshot(1L, 12L, "OPEN", true, past),
                new MaintenanceTicketSnapshot(2L, 12L, "IN_PROGRESS", false, null),
                new MaintenanceTicketSnapshot(3L, 12L, "COMPLETED", true, past),
                new MaintenanceTicketSnapshot(4L, 12L, "CANCELLED", true, past),
                new MaintenanceTicketSnapshot(5L, 12L, "FUTURE", true, past),
                new MaintenanceTicketSnapshot(6L, 12L, "FUTURE", true, past)));
        var result = service().getSummary(12, ReportPeriod.DAY).maintenance();
        assertEquals(6, result.total());
        assertEquals(2, result.active());
        assertEquals(1, result.overdue());
        assertEquals(1, result.criticalActive());
        assertEquals(2, result.unknownStatus());
        assertEquals(2L, result.byStatus().get("FUTURE"));
    }

    @Test
    void batchTotalsAreReceivedBottleCountsNotRemainingStock() {
        when(client.listBatches(12)).thenReturn(List.of(
                new InventoryBatchSnapshot(1L, 12L, 3L, 10, null, false),
                new InventoryBatchSnapshot(2L, 12L, 3L, 5, LocalDate.of(2026, 1, 1), true)));
        var result = service().getSummary(12, ReportPeriod.MONTH).batches();
        assertEquals(2, result.total());
        assertEquals(15, result.receivedBottles());
        assertEquals(1, result.expired());
    }

    @Test
    void independentFailuresReturnNullSectionsInsteadOfConfirmedZeros() {
        when(client.listBatches(12)).thenThrow(new UpstreamUnavailableException("operation-service", new RuntimeException()));
        when(client.listMovements(12)).thenThrow(new UpstreamUnavailableException("operation-service", new RuntimeException()));
        var result = service().getSummary(12, ReportPeriod.DAY);
        assertNull(result.batches());
        assertNull(result.consumption());
        assertNotNull(result.maintenance());
        assertEquals(0, result.maintenance().total());
        assertEquals("ok", result.upstream().get("operation-maintenance"));
    }

    @Test
    void weekAndMonthUseInclusiveCalendarRanges() {
        var week = service().getSummary(12, ReportPeriod.WEEK).consumption();
        assertEquals(LocalDate.of(2026, 11, 9), week.startDate());
        assertEquals(LocalDate.of(2026, 11, 15), week.endDate());
        var month = service().getSummary(12, ReportPeriod.MONTH).consumption();
        assertEquals(LocalDate.of(2026, 11, 1), month.startDate());
        assertEquals(LocalDate.of(2026, 11, 30), month.endDate());
    }
}
