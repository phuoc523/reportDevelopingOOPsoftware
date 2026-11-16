package com.scentstation.reporting.service;

import com.scentstation.reporting.client.MachineSnapshotClient;
import com.scentstation.reporting.client.OperationInventoryClient;
import com.scentstation.reporting.client.UpstreamUnavailableException;
import com.scentstation.reporting.dto.DashboardResponse;
import com.scentstation.reporting.dto.DashboardResponse.AlertStatusSummary;
import com.scentstation.reporting.dto.DashboardResponse.InventoryStatusSummary;
import com.scentstation.reporting.dto.DashboardResponse.MachineStatusSummary;
import com.scentstation.reporting.dto.TransactionSummaryResponse;
import com.scentstation.reporting.dto.OperationReportResponse;
import com.scentstation.reporting.dto.integration.BottleSnapshot;
import com.scentstation.reporting.dto.integration.MachineAlertSnapshot;
import com.scentstation.reporting.dto.integration.MachineSnapshot;
import com.scentstation.reporting.dto.integration.LocationSnapshot;
import com.scentstation.reporting.dto.DashboardResponse.MachineLocationSummary;
import com.scentstation.reporting.enumtype.ReportPeriod;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;

@Service
public class DashboardAggregationService {

    private final TransactionReportService transactionReportService;
    private final MachineSnapshotClient machineSnapshotClient;
    private final OperationInventoryClient operationInventoryClient;
    private final OperationReportService operationReportService;

    public DashboardAggregationService(
            TransactionReportService transactionReportService,
            MachineSnapshotClient machineSnapshotClient,
            OperationInventoryClient operationInventoryClient,
            OperationReportService operationReportService) {
        this.transactionReportService = transactionReportService;
        this.machineSnapshotClient = machineSnapshotClient;
        this.operationInventoryClient = operationInventoryClient;
        this.operationReportService = operationReportService;
    }

    public DashboardResponse getDashboard(String tenantId, ReportPeriod period) {
        return getDashboard(tenantId, period, true);
    }

    public DashboardResponse getDashboard(String tenantId, ReportPeriod period, boolean includePlatformTransactions) {
        tenantId = tenantId == null ? null : tenantId.trim();
        TransactionSummaryResponse transactions = includePlatformTransactions ? transactionReportService.getSummary(period, null, null) : null;
        Map<String, String> upstream = new LinkedHashMap<>();
        upstream.put("order-service", includePlatformTransactions ? "local-copy" : "unsupported-tenant-transaction-schema");

        MachineStatusSummary machines = emptyMachines();
        AlertStatusSummary alerts = emptyAlerts();
        InventoryStatusSummary inventory = emptyInventory();
        List<MachineSnapshot> machineSnapshots = List.of();
        List<LocationSnapshot> locations = List.of();
        OperationReportResponse operations = null;

        if (tenantId != null && !tenantId.isBlank()) {
            try {
                machineSnapshots = machineSnapshotClient.listMachines(tenantId);
                machines = summarizeMachines(machineSnapshots);
                upstream.put("machine-service", "ok");
            } catch (UpstreamUnavailableException ex) {
                upstream.put("machine-service", "unavailable");
            }
            try {
                locations = machineSnapshotClient.listLocations(tenantId);
                upstream.put("machine-locations", "ok");
            } catch (UpstreamUnavailableException ex) {
                upstream.put("machine-locations", "unavailable");
            }
            try {
                List<MachineAlertSnapshot> alertSnapshots = machineSnapshotClient.listAlerts(tenantId);
                alerts = summarizeAlerts(alertSnapshots);
                upstream.put("machine-alerts", "ok");
            } catch (UpstreamUnavailableException ex) {
                upstream.put("machine-alerts", "unavailable");
            }

            try {
                long operationTenantId = Long.parseLong(tenantId);
                if (operationTenantId <= 0) { throw new NumberFormatException(); }
                try {
                    List<BottleSnapshot> bottles = operationInventoryClient.listBottles(operationTenantId);
                    inventory = summarizeInventory(bottles);
                    upstream.put("operation-service", "ok");
                } catch (UpstreamUnavailableException ex) {
                    upstream.put("operation-service", "unavailable");
                }
                operations = operationReportService.getSummary(operationTenantId, period);
                if (operations != null) { upstream.putAll(operations.upstream()); }
            } catch (NumberFormatException ex) {
                String reason = tenantId.matches("-?\\d+") ? "skipped-invalid-tenant" : "skipped-non-numeric-tenant";
                markOperationSkipped(upstream, reason);
            }
        } else {
            upstream.put("machine-service", "skipped-missing-tenant");
            upstream.put("machine-locations", "skipped-missing-tenant");
            upstream.put("machine-alerts", "skipped-missing-tenant");
            markOperationSkipped(upstream, "skipped-missing-tenant");
        }

        List<MachineLocationSummary> distribution = "ok".equals(upstream.get("machine-service"))
                ? summarizeLocations(machineSnapshots, locations) : List.of();
        return new DashboardResponse(transactions, machines, alerts, inventory, upstream, distribution, operations);
    }

    private void markOperationSkipped(Map<String, String> upstream, String reason) {
        for (String key : List.of("operation-service", "operation-batches", "operation-consumption", "operation-maintenance")) {
            upstream.put(key, reason);
        }
        upstream.put("operation-failures", "unsupported-missing-list-api");
    }

    private List<MachineLocationSummary> summarizeLocations(
            List<MachineSnapshot> machines, List<LocationSnapshot> locations) {
        Map<String, LocationSnapshot> metadata = new LinkedHashMap<>();
        Map<String, List<MachineSnapshot>> grouped = new LinkedHashMap<>();
        for (LocationSnapshot location : locations) {
            metadata.put(location.locationId(), location);
            grouped.putIfAbsent(location.locationId(), new ArrayList<>());
        }
        for (MachineSnapshot machine : machines) {
            String locationId = machine.locationId();
            if (locationId != null && locationId.isBlank()) {
                locationId = null;
            }
            grouped.computeIfAbsent(locationId, key -> new ArrayList<>()).add(machine);
        }
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.nullsLast(Comparator.naturalOrder())))
                .map(entry -> {
                    LocationSnapshot location = metadata.get(entry.getKey());
                    return new MachineLocationSummary(entry.getKey(),
                            location == null ? null : location.name(),
                            location == null ? null : location.active(), location != null,
                            summarizeMachines(entry.getValue()));
                }).toList();
    }

    private MachineStatusSummary summarizeMachines(List<MachineSnapshot> machines) {
        int online = 0;
        int offline = 0;
        int unstable = 0;
        int disabled = 0;
        int maintenance = 0;
        int error = 0;
        int unknown = 0;
        for (MachineSnapshot machine : machines) {
            String status = normalize(machine.operationalStatus());
            switch (status) {
                case "ONLINE" -> online++;
                case "OFFLINE" -> offline++;
                case "UNSTABLE" -> unstable++;
                case "DISABLED" -> disabled++;
                case "MAINTENANCE" -> maintenance++;
                case "ERROR" -> error++;
                default -> unknown++;
            }
        }
        return new MachineStatusSummary(
                machines.size(), online, offline, unstable, disabled, maintenance, error, unknown);
    }

    private AlertStatusSummary summarizeAlerts(List<MachineAlertSnapshot> alerts) {
        int open = 0;
        int acknowledged = 0;
        int assigned = 0;
        int escalated = 0;
        int resolved = 0;
        for (MachineAlertSnapshot alert : alerts) {
            switch (normalize(alert.status())) {
                case "OPEN" -> open++;
                case "ACKNOWLEDGED" -> acknowledged++;
                case "ASSIGNED" -> assigned++;
                case "ESCALATED" -> escalated++;
                case "RESOLVED" -> resolved++;
                default -> {
                }
            }
        }
        return new AlertStatusSummary(alerts.size(), open, acknowledged, assigned, escalated, resolved);
    }

    private InventoryStatusSummary summarizeInventory(List<BottleSnapshot> bottles) {
        int available = 0;
        int installed = 0;
        int nearlyEmpty = 0;
        int empty = 0;
        int damaged = 0;
        int expired = 0;
        for (BottleSnapshot bottle : bottles) {
            switch (normalize(bottle.status())) {
                case "AVAILABLE" -> available++;
                case "INSTALLED" -> installed++;
                case "NEARLY_EMPTY" -> nearlyEmpty++;
                case "EMPTY" -> empty++;
                case "DAMAGED" -> damaged++;
                case "EXPIRED" -> expired++;
                default -> {
                }
            }
        }
        return new InventoryStatusSummary(
                bottles.size(), available, installed, nearlyEmpty, empty, damaged, expired);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static MachineStatusSummary emptyMachines() {
        return new MachineStatusSummary(0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static AlertStatusSummary emptyAlerts() {
        return new AlertStatusSummary(0, 0, 0, 0, 0, 0);
    }

    private static InventoryStatusSummary emptyInventory() {
        return new InventoryStatusSummary(0, 0, 0, 0, 0, 0, 0);
    }
}
