package com.scentstation.operation.inventory.integration.machine.exception;

public class MachineSlotProductMismatchException
        extends RuntimeException {

    public MachineSlotProductMismatchException(
            Long machineId,
            Long slotId,
            Long bottleProductId,
            Long slotProductId
    ) {
        super(
                "Bottle product "
                        + bottleProductId
                        + " does not match product "
                        + slotProductId
                        + " configured for machine "
                        + machineId
                        + " slot "
                        + slotId
        );
    }
}