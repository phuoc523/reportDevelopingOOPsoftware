package com.scentstation.operation.inventory.installation.exception;

public class BottleAlreadyInstalledException extends RuntimeException {

    public BottleAlreadyInstalledException(
            Long bottleId
    ) {
        super(
                "Bottle "
                        + bottleId
                        + " is already installed in another slot"
        );
    }
}