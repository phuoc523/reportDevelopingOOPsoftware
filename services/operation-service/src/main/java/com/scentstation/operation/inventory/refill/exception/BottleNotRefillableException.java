package com.scentstation.operation.inventory.refill.exception;

public class BottleNotRefillableException extends RuntimeException {

    public BottleNotRefillableException(
            Long bottleId,
            String status
    ) {
        super(
                "Bottle "
                        + bottleId
                        + " cannot be refilled with status "
                        + status
        );
    }
}