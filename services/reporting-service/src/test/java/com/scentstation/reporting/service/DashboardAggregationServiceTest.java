package com.scentstation.reporting.service;

import com.scentstation.reporting.client.MachineSnapshotClient;
import com.scentstation.reporting.client.OperationInventoryClient;
import com.scentstation.reporting.client.UpstreamUnavailableException;
import com.scentstation.reporting.dto.DashboardResponse;
import com.scentstation.reporting.dto.TransactionSummaryResponse;
import com.scentstation.reporting.dto.integration.BottleSnapshot;
import com.scentstation.reporting.dto.integration.MachineAlertSnapshot;
import com.scentstation.reporting.dto.integration.MachineSnapshot;
import com.scentstation.reporting.dto.integration.LocationSnapshot;
import com.scentstation.reporting.enumtype.ReportPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DashboardAggregationServiceTest {

    @Mock
    private TransactionReportService transactionReportService;

    @Mock
    private MachineSnapshotClient machineSnapshotClient;

    @Mock
    private OperationInventoryClient operationInventoryClient;

    @Mock
    private OperationReportService operationReportService;

    @InjectMocks
    private DashboardAggregationService dashboardAggregationService;

    @Test
    void dashboardAggregatesUpstreamSnapshots() {
        TransactionSummaryResponse summary = new TransactionSummaryResponse(
                1, 1, 0, 0, 0, 0, new BigDecimal("10.00"), "month",
                LocalDate.now(), LocalDate.now());
        when(transactionReportService.getSummary(ReportPeriod.MONTH, null, null)).thenReturn(summary);
        when(machineSnapshotClient.listMachines("12")).thenReturn(List.of(
                new MachineSnapshot(1L, "SN-1", "12", "loc", "A", "ONLINE"),
                new MachineSnapshot(2L, "SN-2", "12", "loc", "B", "MAINTENANCE")));
        when(machineSnapshotClient.listAlerts("12")).thenReturn(List.of(
                new MachineAlertSnapshot(9L, "OFFLINE", "HIGH", "OPEN", 2L, "loc")));
        when(operationInventoryClient.listBottles(12L)).thenReturn(List.of(
                new BottleSnapshot(3L, 12L, 1L, "BTL-1", new BigDecimal("10"), "NEARLY_EMPTY")));

        DashboardResponse dashboard = dashboardAggregationService.getDashboard("12", ReportPeriod.MONTH);

        assertEquals(2, dashboard.machines().total());
        assertEquals(1, dashboard.machines().online());
        assertEquals(1, dashboard.machines().maintenance());
        assertEquals(1, dashboard.alerts().open());
        assertEquals(1, dashboard.inventory().nearlyEmpty());
        assertEquals("ok", dashboard.upstream().get("machine-service"));
        assertEquals("ok", dashboard.upstream().get("operation-service"));
    }

    @Test
    void dashboardDegradesWhenMachineServiceIsDown() {
        TransactionSummaryResponse summary = new TransactionSummaryResponse(
                0, 0, 0, 0, 0, 0, BigDecimal.ZERO, "day",
                LocalDate.now(), LocalDate.now());
        when(transactionReportService.getSummary(ReportPeriod.DAY, null, null)).thenReturn(summary);
        when(machineSnapshotClient.listMachines("12"))
                .thenThrow(new UpstreamUnavailableException("machine-service", new RuntimeException("down")));

        DashboardResponse dashboard = dashboardAggregationService.getDashboard("12", ReportPeriod.DAY);

        assertEquals(0, dashboard.machines().total());
        assertEquals("unavailable", dashboard.upstream().get("machine-service"));
        assertEquals("local-copy", dashboard.upstream().get("order-service"));
    }

    @Test
    void locationsIncludeStatusBreakdownAndEmptyRegisteredLocations() {
        when(machineSnapshotClient.listMachines("12")).thenReturn(List.of(
                new MachineSnapshot(1L, "SN1", "12", "A", "One", "ONLINE"),
                new MachineSnapshot(2L, "SN2", "12", "A", "Two", "OFFLINE"),
                new MachineSnapshot(3L, "SN3", "12", "B", "Three", "MAINTENANCE")));
        when(machineSnapshotClient.listLocations("12")).thenReturn(List.of(
                new LocationSnapshot("A", "12", "Lobby", true),
                new LocationSnapshot("B", "12", "Store", false),
                new LocationSnapshot("C", "12", "Empty", true)));
        var result = dashboardAggregationService.getDashboard("12", ReportPeriod.DAY);
        assertEquals(3, result.machineLocations().size());
        var lobby = result.machineLocations().get(0);
        assertEquals("Lobby", lobby.name());
        assertEquals(true, lobby.active());
        assertEquals(true, lobby.metadataAvailable());
        assertEquals(2, lobby.machines().total());
        assertEquals(1, lobby.machines().online());
        assertEquals(1, lobby.machines().offline());
        assertEquals(false, result.machineLocations().get(1).active());
        assertEquals(0, result.machineLocations().get(2).machines().total());
        assertEquals("ok", result.upstream().get("machine-locations"));
    }

    @Test
    void missingLocationMetadataDoesNotLoseMachines() {
        when(machineSnapshotClient.listMachines("12")).thenReturn(List.of(
                new MachineSnapshot(1L, "SN1", "12", "missing", "One", "ONLINE"),
                new MachineSnapshot(2L, "SN2", "12", null, "Two", "OFFLINE")));
        when(machineSnapshotClient.listLocations("12")).thenThrow(
                new UpstreamUnavailableException("machine-service", new RuntimeException("down")));
        var result = dashboardAggregationService.getDashboard("12", ReportPeriod.DAY);
        assertEquals(2, result.machineLocations().size());
        assertEquals("missing", result.machineLocations().get(0).locationId());
        assertEquals(false, result.machineLocations().get(0).metadataAvailable());
        assertEquals(null, result.machineLocations().get(0).active());
        assertEquals(null, result.machineLocations().get(1).locationId());
        assertEquals(2, result.machineLocations().stream().mapToInt(row -> row.machines().total()).sum());
        assertEquals("unavailable", result.upstream().get("machine-locations"));
        assertEquals("ok", result.upstream().get("machine-service"));
    }

    @Test
    void machineFailureDoesNotProduceConfirmedZeroCountsForLocations() {
        when(machineSnapshotClient.listMachines("12")).thenThrow(
                new UpstreamUnavailableException("machine-service", new RuntimeException("down")));
        when(machineSnapshotClient.listLocations("12")).thenReturn(List.of(
                new LocationSnapshot("A", "12", "Lobby", true)));
        var result = dashboardAggregationService.getDashboard("12", ReportPeriod.DAY);
        assertEquals(List.of(), result.machineLocations());
        assertEquals("unavailable", result.upstream().get("machine-service"));
        assertEquals("ok", result.upstream().get("machine-locations"));
    }

    @Test
    void missingTenantSkipsTenantScopedApis() {
        var result = dashboardAggregationService.getDashboard(null, ReportPeriod.DAY);
        assertEquals("skipped-missing-tenant", result.upstream().get("machine-service"));
        assertEquals("skipped-missing-tenant", result.upstream().get("machine-locations"));
        verifyNoInteractions(machineSnapshotClient, operationInventoryClient);
    }

    @Test
    void unknownStatusIsCountedWithoutCallingItOffline() {
        when(machineSnapshotClient.listMachines("12")).thenReturn(List.of(
                new MachineSnapshot(1L, "SN1", "12", "A", "One", "FUTURE_STATUS")));
        var result = dashboardAggregationService.getDashboard("12", ReportPeriod.DAY);
        assertEquals(1, result.machines().total());
        assertEquals(1, result.machines().unknown());
        assertEquals(0, result.machines().offline());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"0", "-1", "999999999999999999999999"})
    void invalidNumericTenantSkipsOperationApis(String tenant) {
        var result = dashboardAggregationService.getDashboard(tenant, ReportPeriod.DAY);
        assertEquals("skipped-invalid-tenant", result.upstream().get("operation-service"));
        verifyNoInteractions(operationInventoryClient, operationReportService);
    }

    @Test
    void bottleFailureDoesNotPreventOtherOperationSections() {
        when(operationInventoryClient.listBottles(12)).thenThrow(
                new UpstreamUnavailableException("operation-service", new RuntimeException("down")));
        var report = new com.scentstation.reporting.dto.OperationReportResponse(
                new com.scentstation.reporting.dto.OperationReportResponse.BatchSummary(1, 5, 0), null, null,
                java.util.Map.of("operation-batches", "ok"));
        when(operationReportService.getSummary(12, ReportPeriod.DAY)).thenReturn(report);
        var result = dashboardAggregationService.getDashboard("12", ReportPeriod.DAY);
        assertEquals("unavailable", result.upstream().get("operation-service"));
        assertEquals("ok", result.upstream().get("operation-batches"));
        assertEquals(report, result.operations());
    }

    @Test
    void tenantDashboardNeverQueriesGlobalTransactions() {
        var result = dashboardAggregationService.getDashboard("12", ReportPeriod.DAY, false);
        assertEquals(null, result.transactions());
        assertEquals("unsupported-tenant-transaction-schema", result.upstream().get("order-service"));
        verifyNoInteractions(transactionReportService);
    }
}
