package com.scentstation.operation.inventory.batch.exception;

import java.time.LocalDate;

public class InventoryBatchExpiredException extends RuntimeException {

    public InventoryBatchExpiredException(
            Long batchId,
            LocalDate expiryDate
    ) {
        super(
                "Inventory batch "
                        + batchId
                        + " expired on "
                        + expiryDate
        );
    }
}