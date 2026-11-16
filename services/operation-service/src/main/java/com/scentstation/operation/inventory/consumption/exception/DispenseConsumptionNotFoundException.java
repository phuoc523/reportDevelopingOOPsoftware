package com.scentstation.operation.inventory.consumption.exception;

public class DispenseConsumptionNotFoundException
        extends RuntimeException {

    public DispenseConsumptionNotFoundException(
            Long tenantId,
            String commandId
    ) {
        super(
                "Dispense consumption not found for tenant "
                        + tenantId
                        + " with commandId "
                        + commandId
        );
    }
}