package com.scentstation.operation.inventory.integration.machine.client;

import com.scentstation.operation.inventory.integration.machine.dto.MachineSlotInfo;
import org.springframework.stereotype.Component;

@Component
public class LocalMachineSlotClient implements MachineSlotClient {

    @Override
    public MachineSlotInfo getSlot(
            Long tenantId,
            Long machineId,
            Long slotId
    ) {
        return MachineSlotInfo.builder()
                .tenantId(tenantId)
                .machineId(machineId)
                .slotId(slotId)
                .active(true)
                .maintenanceMode(false)
                .build();
    }
}