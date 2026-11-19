package com.scentstation.reporting.service;

import com.scentstation.reporting.client.MachineSnapshotClient;
import com.scentstation.reporting.client.OperationInventoryClient;
import com.scentstation.reporting.dto.integration.DispenseCommandSnapshot;
import com.scentstation.reporting.dto.integration.MachineSnapshot;
import com.scentstation.reporting.repository.TransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalAnalyticsServiceTest {
    @Test
    void separatesDiagnosticSpraysAndFindsPaidOrdersWithoutConfirmation() {
        MachineSnapshotClient machines = mock(MachineSnapshotClient.class);
        OperationInventoryClient operations = mock(OperationInventoryClient.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        when(machines.listMachines("12")).thenReturn(List.of(
                new MachineSnapshot(1L, "SN-1", "12", "7", "Kiosk", "ONLINE")));
        when(machines.listAlerts("12")).thenReturn(List.of());
        when(machines.listDispenseCommands("12")).thenReturn(List.of(
                new DispenseCommandSnapshot("customer-ok", "12", 1L, "1", "100",
                        "CUSTOMER", "DISPENSED", Instant.parse("2026-11-19T02:00:00Z"),
                        null, Instant.parse("2026-11-19T02:01:00Z"), true),
                new DispenseCommandSnapshot("diagnostic-ok", "12", 1L, "1", null,
                        "DIAGNOSTIC", "DISPENSED", Instant.parse("2026-11-19T03:00:00Z"),
                        null, Instant.parse("2026-11-19T03:01:00Z"), true)));
        when(operations.listBottles(12L)).thenReturn(List.of());
        when(operations.listMovements(12L)).thenReturn(List.of());
        when(operations.listMaintenanceTickets(12L)).thenReturn(List.of());
        when(transactions.findPaidOrderIds(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("12"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(List.of("100", "101"));

        var service = new OperationalAnalyticsService(machines, operations, transactions,
                ZoneOffset.UTC, Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"),
                        ZoneId.of("Asia/Ho_Chi_Minh")));
        var result = service.report("12", LocalDate.of(2026, 11, 19),
                LocalDate.of(2026, 11, 19), null, null, null, null);

        assertEquals(1, result.sprays().customerSuccessful());
        assertEquals(1, result.sprays().diagnosticSuccessful());
        assertEquals(1, result.sprays().paidWithoutSprayConfirmation());
    }
}
