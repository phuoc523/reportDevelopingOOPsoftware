package com.scentstation.reporting.service;

import com.scentstation.reporting.client.MachineSnapshotClient;
import com.scentstation.reporting.client.OperationInventoryClient;
import com.scentstation.reporting.dto.integration.BottleSnapshot;
import com.scentstation.reporting.dto.integration.DispenseCommandSnapshot;
import com.scentstation.reporting.dto.integration.InventoryMovementSnapshot;
import com.scentstation.reporting.dto.integration.MachineAlertSnapshot;
import com.scentstation.reporting.dto.integration.MachineSnapshot;
import com.scentstation.reporting.dto.integration.MaintenanceTicketSnapshot;
import com.scentstation.reporting.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
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

    @Test
    void refillCandidatesResolveMachineAndSlotFromPriorInstallMovements() {
        MachineSnapshotClient machines = mock(MachineSnapshotClient.class);
        OperationInventoryClient operations = mock(OperationInventoryClient.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        when(machines.listMachines("12")).thenReturn(List.of(
                new MachineSnapshot(1L, "SN-1", "12", "7", "Kiosk", "ONLINE")));
        when(machines.listAlerts("12")).thenReturn(List.of());
        when(machines.listDispenseCommands("12")).thenReturn(List.of());
        when(operations.listBottles(12L)).thenReturn(List.of(
                new BottleSnapshot(50L, 12L, 91L, "BTL-50", new BigDecimal("4.50"), "NEARLY_EMPTY")));
        // Bottle was installed BEFORE the report date range (Nov 1 vs report range Nov 19)
        when(operations.listMovements(12L)).thenReturn(List.of(
                new InventoryMovementSnapshot(1L, 12L, 50L, "INSTALL", 1L, 3L,
                        new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO,
                        1L, "initial install", "INST-1", LocalDateTime.of(2026, 11, 1, 8, 0))));
        when(operations.listMaintenanceTickets(12L)).thenReturn(List.of());
        when(transactions.findPaidOrderIds(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("12"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(List.of());

        var service = new OperationalAnalyticsService(machines, operations, transactions,
                ZoneOffset.UTC, Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"),
                        ZoneId.of("Asia/Ho_Chi_Minh")));
        var result = service.report("12", LocalDate.of(2026, 11, 19),
                LocalDate.of(2026, 11, 19), null, null, null, new BigDecimal("10"));

        assertEquals(1, result.inventory().refillCandidates().size());
        var candidate = result.inventory().refillCandidates().get(0);
        assertEquals(50L, candidate.bottleId());
        assertEquals(1L, candidate.machineId());
        assertEquals(3L, candidate.slotId());
    }

    @Test
    void assignedMachineAndLocationScopesFilterTenantDataWhenQueryFiltersOmitted() {
        MachineSnapshotClient machines = mock(MachineSnapshotClient.class);
        OperationInventoryClient operations = mock(OperationInventoryClient.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        when(machines.listMachines("12")).thenReturn(List.of(
                new MachineSnapshot(1L, "SN-1", "12", "7", "Kiosk A", "ONLINE"),
                new MachineSnapshot(2L, "SN-2", "12", "8", "Kiosk B", "ONLINE")));
        when(machines.listAlerts("12")).thenReturn(List.of());
        when(machines.listDispenseCommands("12")).thenReturn(List.of(
                new DispenseCommandSnapshot("cmd-1", "12", 1L, "1", "100",
                        "CUSTOMER", "DISPENSED", Instant.parse("2026-11-19T02:00:00Z"),
                        null, Instant.parse("2026-11-19T02:01:00Z"), true),
                new DispenseCommandSnapshot("cmd-2", "12", 2L, "1", "200",
                        "CUSTOMER", "DISPENSED", Instant.parse("2026-11-19T02:05:00Z"),
                        null, Instant.parse("2026-11-19T02:06:00Z"), true)));
        when(operations.listBottles(12L)).thenReturn(List.of(
                new BottleSnapshot(50L, 12L, 91L, "BTL-50", new BigDecimal("5.00"), "NEARLY_EMPTY"),
                new BottleSnapshot(51L, 12L, 92L, "BTL-51", new BigDecimal("5.00"), "NEARLY_EMPTY")));
        when(operations.listMovements(12L)).thenReturn(List.of(
                new InventoryMovementSnapshot(1L, 12L, 50L, "INSTALL", 1L, 3L,
                        new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO,
                        1L, "install", "INST-1", LocalDateTime.of(2026, 11, 1, 8, 0)),
                new InventoryMovementSnapshot(2L, 12L, 51L, "INSTALL", 2L, 4L,
                        new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO,
                        1L, "install", "INST-2", LocalDateTime.of(2026, 11, 1, 8, 0))));
        when(operations.listMaintenanceTickets(12L)).thenReturn(List.of());
        when(transactions.findPaidOrderIdsInMachines(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("12"),
                org.mockito.ArgumentMatchers.eq(List.of("1")), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(List.of("100", "101"));

        var service = new OperationalAnalyticsService(machines, operations, transactions,
                ZoneOffset.UTC, Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"),
                        ZoneId.of("Asia/Ho_Chi_Minh")));
        var result = service.report("12", LocalDate.of(2026, 11, 19),
                LocalDate.of(2026, 11, 19), null, null, null, null,
                Set.of(1L), Set.of(7L));

        assertEquals(1, result.machines().total());
        assertEquals(1L, result.machines().details().get(0).machineId());
        assertEquals(1, result.sprays().customerSuccessful());
        assertEquals(1, result.sprays().paidWithoutSprayConfirmation());
        assertEquals(1, result.inventory().refillCandidates().size());
        assertEquals(50L, result.inventory().refillCandidates().get(0).bottleId());
    }

    @Test
    void computesLocationRatesDailyTrendsStockAdjustmentsAbnormalConsumptionAndOverdueTickets() {
        MachineSnapshotClient machines = mock(MachineSnapshotClient.class);
        OperationInventoryClient operations = mock(OperationInventoryClient.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        when(machines.listMachines("12")).thenReturn(List.of(
                new MachineSnapshot(1L, "SN-1", "12", "LOC-A", "Kiosk A", "ONLINE")));
        when(machines.listAlerts("12")).thenReturn(List.of(
                new MachineAlertSnapshot(10L, "OFFLINE", "HIGH", "RESOLVED", 1L, "LOC-A",
                        Instant.parse("2026-11-19T01:00:00Z"), Instant.parse("2026-11-19T03:00:00Z"),
                        Instant.parse("2026-11-19T01:30:00Z"), null,
                        Instant.parse("2026-11-19T03:00:00Z"))));
        when(machines.listDispenseCommands("12")).thenReturn(List.of(
                new DispenseCommandSnapshot("c-1", "12", 1L, "1", "100",
                        "CUSTOMER", "DISPENSED", Instant.parse("2026-11-19T02:00:00Z"),
                        null, Instant.parse("2026-11-19T02:01:00Z"), true),
                new DispenseCommandSnapshot("c-2", "12", 1L, "1", "102",
                        "CUSTOMER", "FAILED", Instant.parse("2026-11-19T03:00:00Z"),
                        null, Instant.parse("2026-11-19T03:01:00Z"), false)));
        when(operations.listBottles(12L)).thenReturn(List.of(
                new BottleSnapshot(50L, 12L, 91L, 1L, "BTL-50", new BigDecimal("100"),
                        new BigDecimal("40"), new BigDecimal("0.5"), "INSTALLED")));
        when(operations.listMovements(12L)).thenReturn(List.of(
                new InventoryMovementSnapshot(1L, 12L, 50L, "INSTALL", 1L, 3L,
                        new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO,
                        1L, "install", "INST-1", LocalDateTime.of(2026, 11, 1, 8, 0)),
                new InventoryMovementSnapshot(2L, 12L, 50L, "CUSTOMER_CONSUMPTION", 1L, 3L,
                        new BigDecimal("100"), new BigDecimal("97"), new BigDecimal("-3"),
                        1L, "spray", "c-1", LocalDateTime.of(2026, 11, 19, 2, 1)),
                new InventoryMovementSnapshot(3L, 12L, 50L, "ADJUSTMENT", 1L, 3L,
                        new BigDecimal("97"), new BigDecimal("40"), new BigDecimal("-57"),
                        2L, "leak check", "ADJ-1", LocalDateTime.of(2026, 11, 19, 4, 0))));
        when(operations.listMaintenanceTickets(12L)).thenReturn(List.of(
                new MaintenanceTicketSnapshot(7L, 12L, 1L, 1L, 3L, 50L, "HARDWARE", "HIGH", "HIGH",
                        "OPEN", 2L, true, LocalDateTime.of(2026, 11, 19, 5, 0),
                        LocalDateTime.of(2026, 11, 19, 1, 0), null, null)));
        when(transactions.findPaidOrderIds(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("12"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(List.of("100", "101"));

        var service = new OperationalAnalyticsService(machines, operations, transactions,
                ZoneOffset.UTC, Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"),
                        ZoneId.of("Asia/Ho_Chi_Minh")));
        var result = service.report("12", LocalDate.of(2026, 11, 19),
                LocalDate.of(2026, 11, 19), null, null, null, null);

        assertEquals(List.of("101"), result.sprays().paidOrderIdsWithoutSprayConfirmation());
        assertEquals(50.0, result.sprays().successRateByLocation().get("LOC-A"));
        assertEquals(1, result.sprays().dailyTrend().size());
        assertEquals(2L, result.sprays().dailyTrend().get(0).total());
        assertEquals(1, result.inventory().stockByMachineSlot().size());
        assertEquals(3L, result.inventory().stockByMachineSlot().get(0).slotId());
        assertEquals(new BigDecimal("3"), result.inventory().dailyConsumption().get(LocalDate.of(2026, 11, 19)));
        assertEquals(1, result.inventory().adjustmentHistory().size());
        assertEquals(2, result.inventory().abnormalConsumptions().size());
        assertEquals(1L, result.incidents().overdueMaintenanceTickets());
        assertEquals(2.0, result.incidents().averageAlertResolutionHoursByMachine().get(1L));
        assertEquals(2.0, result.incidents().averageAlertResolutionHoursByLocation().get("LOC-A"));
        assertEquals(2.0, result.incidents().alertDowntimeHours());
    }
}
