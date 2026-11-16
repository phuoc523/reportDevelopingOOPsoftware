package com.scentstation.operation.inventory.consumption.exception;

public class BottleInstallationMismatchException extends RuntimeException {

    public BottleInstallationMismatchException(
            Long bottleId,
            Long machineId,
            Long slotId
    ) {
        super(
                "Bottle "
                        + bottleId
                        + " is not actively installed on machine "
                        + machineId
                        + " slot "
                        + slotId
        );
    }
}