package com.scentstation.order.dto.response;

public record PublicPaymentResponse(
        PaymentResponse payment,
        PublicOrderDetailsResponse order
) {
}
