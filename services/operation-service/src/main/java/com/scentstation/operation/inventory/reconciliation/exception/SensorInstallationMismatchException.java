package com.scentstation.operation.inventory.reconciliation.exception;

public class SensorInstallationMismatchException
        extends RuntimeException {

    public SensorInstallationMismatchException(
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