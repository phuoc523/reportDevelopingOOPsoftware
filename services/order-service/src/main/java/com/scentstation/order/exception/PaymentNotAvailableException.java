package com.scentstation.order.exception;

public class PaymentNotAvailableException extends RuntimeException {

    public PaymentNotAvailableException(Long orderId) {
        super("QR payment is only available for a PENDING order: " + orderId);
    }
}
