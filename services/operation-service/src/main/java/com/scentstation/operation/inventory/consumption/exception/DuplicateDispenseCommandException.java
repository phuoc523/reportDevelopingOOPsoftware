package com.scentstation.operation.inventory.consumption.exception;

public class DuplicateDispenseCommandException extends RuntimeException {

    public DuplicateDispenseCommandException(
            Long tenantId,
            String commandId
    ) {
        super(
                "Dispense command "
                        + commandId
                        + " has already been processed for tenant "
                        + tenantId
        );
    }
}