package com.scentstation.order.exception;

public class PaidOrderCancellationException extends RuntimeException {

    public PaidOrderCancellationException(Long orderId) {
        super("A paid order cannot be cancelled: " + orderId);
    }
}
