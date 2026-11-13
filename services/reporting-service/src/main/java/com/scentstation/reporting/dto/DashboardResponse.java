package com.scentstation.reporting.dto;

import java.util.Map;
import java.util.List;

public record DashboardResponse(
        TransactionSummaryResponse transactions,
        MachineStatusSummary machines,
        AlertStatusSummary alerts,
        InventoryStatusSummary inventory,
        Map<String, String> upstream,
        List<MachineLocationSummary> machineLocations,
        OperationReportResponse operations,
        ReportAccess access
) {
    public DashboardResponse(TransactionSummaryResponse transactions, MachineStatusSummary machines,
            AlertStatusSummary alerts, InventoryStatusSummary inventory, Map<String, String> upstream,
            List<MachineLocationSummary> machineLocations, OperationReportResponse operations) {
        this(transactions, machines, alerts, inventory, upstream, machineLocations, operations, null);
    }

    public DashboardResponse withAccess(ReportAccess access) {
        return new DashboardResponse(transactions, machines, alerts, inventory, upstream, machineLocations, operations, access);
    }

    public record ReportAccess(String scope, String tenantId,
            boolean canReadFinancialReports, boolean canExport) {}

    public record MachineStatusSummary(
            int total,
            int online,
            int offline,
            int unstable,
            int disabled,
            int maintenance,
            int error,
            int unknown
    ) {
    }

    public record MachineLocationSummary(
            String locationId,
            String name,
            Boolean active,
            boolean metadataAvailable,
            MachineStatusSummary machines
    ) {}

    public record AlertStatusSummary(
            int total,
            int open,
            int acknowledged,
            int assigned,
            int escalated,
            int resolved
    ) {
    }

    public record InventoryStatusSummary(
            int bottles,
            int available,
            int installed,
            int nearlyEmpty,
            int empty,
            int damaged,
            int expired
    ) {
    }
}
