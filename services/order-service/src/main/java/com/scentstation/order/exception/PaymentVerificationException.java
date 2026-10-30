package com.scentstation.order.exception;

import org.springframework.http.HttpStatus;

public class PaymentVerificationException extends RuntimeException {

    private final HttpStatus status;

    public PaymentVerificationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
