package com.scentstation.reporting.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPaymentResponse(
                String reference,
                Long orderId,
                BigDecimal amount,
                String currency,
                String status,
                String qrPayload,
                LocalDateTime createdAt,
                LocalDateTime expiresAt,
                LocalDateTime paidAt,
                String tenantId,
                String machineId,
                String slotId,
                Long productId) {
        public OrderPaymentResponse(
                        String reference,
                        Long orderId,
                        BigDecimal amount,
                        String currency,
                        String status,
                        String qrPayload,
                        LocalDateTime createdAt,
                        LocalDateTime expiresAt,
                        LocalDateTime paidAt) {
                this(reference, orderId, amount, currency, status, qrPayload, createdAt, expiresAt, paidAt,
                                null, null, null, null);
        }
}
