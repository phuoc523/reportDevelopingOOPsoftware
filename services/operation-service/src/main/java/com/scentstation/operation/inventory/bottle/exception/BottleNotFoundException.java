package com.scentstation.operation.inventory.bottle.exception;

public class BottleNotFoundException extends RuntimeException {

    public BottleNotFoundException(
            Long id,
            Long tenantId
    ) {
        super(
                "Bottle not found with id "
                        + id
                        + " for tenant "
                        + tenantId
        );
    }
}