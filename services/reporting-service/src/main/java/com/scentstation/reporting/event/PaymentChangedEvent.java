package com.scentstation.reporting.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentChangedEvent(
        int eventVersion, String eventId, String reference, Long orderId,
        BigDecimal amount, String currency, String status,
        LocalDateTime createdAt, LocalDateTime paidAt, LocalDateTime occurredAt
) {
}
