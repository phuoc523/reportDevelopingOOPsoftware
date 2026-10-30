package com.scentstation.operation.inventory.estimation.exception;

public class SprayCalibrationNotFoundException extends RuntimeException {

    public SprayCalibrationNotFoundException(
            Long bottleId
    ) {
        super(
                "Spray calibration not found for bottle "
                        + bottleId
        );
    }
}