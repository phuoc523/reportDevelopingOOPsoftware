package com.scentstation.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record OperationalReportResponse(
        String tenantId,
        LocalDate startDate,
        LocalDate endDate,
        MachinePerformance machines,
        SprayPerformance sprays,
        InventoryPerformance inventory,
        IncidentPerformance incidents,
        Map<String, String> upstream
) {
    public record MachinePerformance(int total, Map<String, Long> byStatus,
            List<MachineMetric> details) {}

    public record MachineMetric(Long machineId, String locationId, String status,
            long successfulSprays, long failedSprays, long unconfirmedSprays,
            double successRate, long alerts) {}

    public record SprayPerformance(long total, long successful, long failed, long unconfirmed,
            long customerSuccessful, long diagnosticSuccessful, double successRate,
            Long mostSelectedProductId, Map<Long, Long> successfulByProduct,
            Map<Long, Long> successfulByMachine, Map<Long, Long> failedByMachine,
            long paidWithoutSprayConfirmation) {}

    public record InventoryPerformance(BigDecimal totalRemaining, Map<String, Long> bottlesByStatus,
            Map<Long, BigDecimal> remainingByProduct, Map<Long, BigDecimal> consumedByProduct,
            long refills, long replacements, long adjustments,
            List<RefillCandidate> refillCandidates) {}

    public record RefillCandidate(Long bottleId, Long productId, Long machineId, Long slotId,
            BigDecimal currentQuantity, BigDecimal threshold) {}

    public record IncidentPerformance(Map<String, Long> alertsByType,
            Map<String, Long> alertsBySeverity, Map<String, Long> alertsByStatus,
            Map<String, Long> maintenanceByStatus, double averageAcknowledgeHours,
            double averageAlertResolutionHours, double averageMaintenanceResolutionHours,
            double downtimeHours, List<Long> highFailureMachineIds) {}
}
