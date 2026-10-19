package com.scentstation.order.exception;

import com.scentstation.order.entity.OrderStatus;

public class InvalidOrderStatusTransitionException extends RuntimeException {

    public InvalidOrderStatusTransitionException(OrderStatus current, OrderStatus requested) {
        super("Cannot change order status from " + current + " to " + requested);
    }
}
