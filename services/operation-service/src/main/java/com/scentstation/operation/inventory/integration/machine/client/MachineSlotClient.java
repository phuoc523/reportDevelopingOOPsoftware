package com.scentstation.operation.inventory.integration.machine.client;

import com.scentstation.operation.inventory.integration.machine.dto.MachineSlotInfo;

public interface MachineSlotClient {

    // Lấy snapshot slot từ boundary do machine-service sở hữu.
    MachineSlotInfo getSlot(
            Long tenantId,
            Long machineId,
            Long slotId
    );
}
