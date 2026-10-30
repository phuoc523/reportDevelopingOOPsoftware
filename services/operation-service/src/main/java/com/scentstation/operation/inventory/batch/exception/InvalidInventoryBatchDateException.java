package com.scentstation.operation.inventory.batch.exception;

import java.time.LocalDate;

public class InvalidInventoryBatchDateException extends RuntimeException {

    public InvalidInventoryBatchDateException(
            LocalDate receivedAt,
            LocalDate expiryDate
    ) {
        super(
                "Expiry date "
                        + expiryDate
                        + " must not be before received date "
                        + receivedAt
        );
    }
}