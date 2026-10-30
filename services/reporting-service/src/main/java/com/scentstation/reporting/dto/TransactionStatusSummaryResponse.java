package com.scentstation.reporting.dto;

import java.time.LocalDate;

public record TransactionStatusSummaryResponse(
        long totalTransactions,
        long paid,
        long pending,
        long failed,
        long expired,
        long refunded,
        double paidRate,
        double pendingRate,
        double failedRate,
        double expiredRate,
        double refundedRate,
        LocalDate startDate,
        LocalDate endDate
) {}

