package com.scentstation.order.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentCallbackEvent(
        String eventId,
        String reference,
        Long orderId,
        BigDecimal amount,
        String currency,
        String status,
        Outcome outcome,
        int errorStatus,
        String message,
        LocalDateTime receivedAt
) {
    public enum Outcome {
        ACCEPTED,
        REJECTED
    }

    public boolean matches(String callbackReference, Long callbackOrderId, BigDecimal callbackAmount,
                           String callbackCurrency, String callbackStatus) {
        return reference.equals(callbackReference)
                && orderId.equals(callbackOrderId)
                && amount.compareTo(callbackAmount) == 0
                && currency.equals(callbackCurrency)
                && status.equals(callbackStatus);
    }
}
