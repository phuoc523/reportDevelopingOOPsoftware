package com.scentstation.operation.inventory.integration.machine.exception;

public class MachineSlotUnavailableException
        extends RuntimeException {

    public MachineSlotUnavailableException(
            Long machineId,
            Long slotId
    ) {
        super(
                "Machine "
                        + machineId
                        + " slot "
                        + slotId
                        + " is unavailable"
        );
    }
}