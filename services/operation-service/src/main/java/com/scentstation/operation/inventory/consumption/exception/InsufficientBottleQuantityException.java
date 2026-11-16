package com.scentstation.operation.inventory.consumption.exception;

import java.math.BigDecimal;

public class InsufficientBottleQuantityException extends RuntimeException {

    public InsufficientBottleQuantityException(
            Long bottleId,
            BigDecimal available,
            BigDecimal required
    ) {
        super(
                "Bottle "
                        + bottleId
                        + " has insufficient quantity. Available: "
                        + available
                        + ", required: "
                        + required
        );
    }
}