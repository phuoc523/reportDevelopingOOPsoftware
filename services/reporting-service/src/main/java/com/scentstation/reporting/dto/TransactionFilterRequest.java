package com.scentstation.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionFilterRequest(
        String reference,
        String orderId,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String tenantId,
        String machineId,
        String slotId,
        Long productId
) {
    public TransactionFilterRequest(String reference, String orderId, String status,
            LocalDate startDate, LocalDate endDate, BigDecimal minAmount, BigDecimal maxAmount) {
        this(reference, orderId, status, startDate, endDate, minAmount, maxAmount,
                null, null, null, null);
    }
}

