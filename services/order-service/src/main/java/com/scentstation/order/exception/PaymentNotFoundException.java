package com.scentstation.order.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String reference) {
        super("Payment not found: " + reference);
    }
}
