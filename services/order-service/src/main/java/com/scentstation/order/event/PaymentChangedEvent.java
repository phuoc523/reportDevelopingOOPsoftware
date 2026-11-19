package com.scentstation.order.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentChangedEvent(
        int eventVersion,
        String eventId,
        String reference,
        Long orderId,
        String tenantId,
        String machineId,
        String slotId,
        Long productId,
        BigDecimal amount,
        String currency,
        String status,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime occurredAt
) {
    public PaymentChangedEvent(int eventVersion, String eventId, String reference, Long orderId,
            BigDecimal amount, String currency, String status, LocalDateTime createdAt,
            LocalDateTime paidAt, LocalDateTime occurredAt) {
        this(eventVersion, eventId, reference, orderId, null, null, null, null,
                amount, currency, status, createdAt, paidAt, occurredAt);
    }
}
