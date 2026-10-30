package com.scentstation.operation.inventory.batch.exception;

public class InventoryBatchAlreadyExistsException extends RuntimeException {
    public InventoryBatchAlreadyExistsException(Long tenantId, String batchCode) {
        super("Inventory batch already exists for tenant: " + tenantId + " with batch code: " + batchCode);
    }
    
}
