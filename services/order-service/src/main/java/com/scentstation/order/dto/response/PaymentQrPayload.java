package com.scentstation.order.dto.response;

public record PaymentQrPayload(
        String type,
        String paymentReference
) {
}
