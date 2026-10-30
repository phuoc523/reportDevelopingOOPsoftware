package com.scentstation.operation.inventory.installation.exception;

public class BottleNotInstallableException extends RuntimeException {

    public BottleNotInstallableException(
            Long bottleId,
            String status
    ) {
        super(
                "Bottle "
                        + bottleId
                        + " cannot be installed with status "
                        + status
        );
    }
}