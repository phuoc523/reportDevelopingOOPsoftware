package com.scentstation.operation.inventory.batch.exception;

public class InventoryBatchNotFoundException extends RuntimeException {

    public InventoryBatchNotFoundException(
            Long id,
            Long tenantId
    ) {
        super(
                "Inventory batch not found with id "
                        + id
                        + " for tenant "
                        + tenantId
        );
    }
}