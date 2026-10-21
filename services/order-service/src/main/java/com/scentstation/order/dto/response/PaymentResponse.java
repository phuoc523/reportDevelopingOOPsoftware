package com.scentstation.order.dto.response;

import com.scentstation.order.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        String reference,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        String qrPayload,
        LocalDateTime createdAt,
        LocalDateTime paidAt
) {
}
