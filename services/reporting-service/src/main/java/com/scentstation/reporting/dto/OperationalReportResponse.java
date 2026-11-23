package com.scentstation.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
            long paidWithoutSprayConfirmation,
            List<String> paidOrderIdsWithoutSprayConfirmation,
            Map<String, Double> successRateByLocation,
            List<DailySprayMetric> dailyTrend) {
        public SprayPerformance(long total, long successful, long failed, long unconfirmed,
                long customerSuccessful, long diagnosticSuccessful, double successRate,
                Long mostSelectedProductId, Map<Long, Long> successfulByProduct,
                Map<Long, Long> successfulByMachine, Map<Long, Long> failedByMachine,
                long paidWithoutSprayConfirmation) {
            this(total, successful, failed, unconfirmed, customerSuccessful, diagnosticSuccessful,
                    successRate, mostSelectedProductId, successfulByProduct, successfulByMachine,
                    failedByMachine, paidWithoutSprayConfirmation, List.of(), Map.of(), List.of());
        }
    }

    public record DailySprayMetric(LocalDate date, long total, long successful, long failed,
            long unconfirmed, double successRate) {}

    public record InventoryPerformance(BigDecimal totalRemaining, Map<String, Long> bottlesByStatus,
            Map<Long, BigDecimal> remainingByProduct, Map<Long, BigDecimal> consumedByProduct,
            long refills, long replacements, long adjustments,
            List<RefillCandidate> refillCandidates,
            List<SlotStockItem> stockByMachineSlot,
            Map<LocalDate, BigDecimal> dailyConsumption,
            List<InventoryAdjustmentRecord> adjustmentHistory,
            List<AbnormalConsumptionRecord> abnormalConsumptions) {
        public InventoryPerformance(BigDecimal totalRemaining, Map<String, Long> bottlesByStatus,
                Map<Long, BigDecimal> remainingByProduct, Map<Long, BigDecimal> consumedByProduct,
                long refills, long replacements, long adjustments,
                List<RefillCandidate> refillCandidates) {
            this(totalRemaining, bottlesByStatus, remainingByProduct, consumedByProduct,
                    refills, replacements, adjustments, refillCandidates,
                    List.of(), Map.of(), List.of(), List.of());
        }
    }

    public record RefillCandidate(Long bottleId, Long productId, Long machineId, Long slotId,
            BigDecimal currentQuantity, BigDecimal threshold) {}

    public record SlotStockItem(Long bottleId, Long productId, Long machineId, Long slotId,
            BigDecimal currentQuantity, String status) {}

    public record InventoryAdjustmentRecord(Long movementId, Long bottleId, Long machineId,
            Long slotId, BigDecimal quantityBefore, BigDecimal quantityAfter,
            BigDecimal difference, Long actorId, String reason, LocalDateTime createdAt) {}

    public record AbnormalConsumptionRecord(Long movementId, Long bottleId, Long productId,
            Long machineId, BigDecimal consumedQuantity, BigDecimal expectedQuantity,
            String reason) {}

    public record IncidentPerformance(Map<String, Long> alertsByType,
            Map<String, Long> alertsBySeverity, Map<String, Long> alertsByStatus,
            Map<String, Long> maintenanceByStatus, double averageAcknowledgeHours,
            double averageAlertResolutionHours, double averageMaintenanceResolutionHours,
            double downtimeHours, List<Long> highFailureMachineIds,
            long overdueMaintenanceTickets,
            Map<Long, Double> averageAlertResolutionHoursByMachine,
            Map<String, Double> averageAlertResolutionHoursByLocation,
            double alertDowntimeHours) {
        public IncidentPerformance(Map<String, Long> alertsByType,
                Map<String, Long> alertsBySeverity, Map<String, Long> alertsByStatus,
                Map<String, Long> maintenanceByStatus, double averageAcknowledgeHours,
                double averageAlertResolutionHours, double averageMaintenanceResolutionHours,
                double downtimeHours, List<Long> highFailureMachineIds) {
            this(alertsByType, alertsBySeverity, alertsByStatus, maintenanceByStatus,
                    averageAcknowledgeHours, averageAlertResolutionHours,
                    averageMaintenanceResolutionHours, downtimeHours, highFailureMachineIds,
                    0L, Map.of(), Map.of(), 0.0);
        }
    }
}
