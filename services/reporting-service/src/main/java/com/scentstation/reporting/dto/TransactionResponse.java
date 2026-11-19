
package com.scentstation.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        String reference,
        String orderId,
        BigDecimal amount,
        String currency,
        String status,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        String tenantId,
        String machineId,
        String slotId,
        Long productId
) {
    public TransactionResponse(Long id, String reference, String orderId, BigDecimal amount,
            String currency, String status, LocalDateTime createdAt, LocalDateTime paidAt) {
        this(id, reference, orderId, amount, currency, status, createdAt, paidAt,
                null, null, null, null);
    }
}

