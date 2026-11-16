package com.scentstation.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Null sections are unavailable or skipped, never confirmed zero results. */
public record OperationReportResponse(BatchSummary batches, ConsumptionSummary consumption,
        MaintenanceSummary maintenance, Map<String, String> upstream) {
    public record BatchSummary(int total, long receivedBottles, long expired) {}
    public record ConsumptionSummary(LocalDate startDate, LocalDate endDate,
            long successfulCustomerCommands, long successfulDiagnosticCommands,
            BigDecimal customerConsumedQuantity, BigDecimal diagnosticConsumedQuantity,
            Long failedCommands) {}
    public record MaintenanceSummary(int total, Map<String, Long> byStatus,
            long active, long overdue, long criticalActive, long unknownStatus) {}
}
