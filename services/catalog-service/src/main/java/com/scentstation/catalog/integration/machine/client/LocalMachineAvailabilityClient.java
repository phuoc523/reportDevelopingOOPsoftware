package com.scentstation.catalog.integration.machine.client;

import com.scentstation.catalog.integration.machine.dto.MachineAvailabilityInfo;
import org.springframework.stereotype.Component;

@Component
public class LocalMachineAvailabilityClient
        implements MachineAvailabilityClient {

    // Mô phỏng machine availability cho tới khi machine-service có contract thật.
    @Override
    public MachineAvailabilityInfo getProductAvailability(
            Long tenantId,
            Long machineId,
            Long productId
    ) {
        return MachineAvailabilityInfo.builder()
                .tenantId(tenantId)
                .machineId(machineId)
                .productId(productId)
                .available(true)
                .availableSlotCount(1)
                .build();
    }
}