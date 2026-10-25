package com.scentstation.operation.inventory.installation.exception;

public class SlotAlreadyOccupiedException extends RuntimeException {

    public SlotAlreadyOccupiedException(
            Long machineId,
            Long slotId
    ) {
        super(
                "Slot "
                        + slotId
                        + " on machine "
                        + machineId
                        + " already has an active bottle"
        );
    }
}