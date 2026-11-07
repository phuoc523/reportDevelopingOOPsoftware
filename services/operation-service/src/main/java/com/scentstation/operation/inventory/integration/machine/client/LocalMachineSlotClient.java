package com.scentstation.operation.inventory.integration.machine.client;

import com.scentstation.operation.inventory.integration.machine.dto.MachineSlotInfo;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class LocalMachineSlotClient implements MachineSlotClient {

    // Mô phỏng slot khả dụng cho tới khi machine-service có API thật.
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
