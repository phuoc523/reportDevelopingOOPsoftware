package com.scentstation.operation.inventory.integration.machine.client;

import com.scentstation.operation.inventory.integration.machine.dto.MachineSlotInfo;

public interface MachineSlotClient {

    MachineSlotInfo getSlot(
            Long tenantId,
            Long machineId,
            Long slotId
    );
}