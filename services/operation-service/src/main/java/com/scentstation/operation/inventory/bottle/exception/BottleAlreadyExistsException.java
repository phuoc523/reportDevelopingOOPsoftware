package com.scentstation.operation.inventory.bottle.exception;

public class BottleAlreadyExistsException extends RuntimeException {

    public BottleAlreadyExistsException(
            Long tenantId,
            String bottleCode
    ) {
        super(
                "Bottle already exists for tenant "
                        + tenantId
                        + " with bottle code: "
                        + bottleCode
        );
    }
}