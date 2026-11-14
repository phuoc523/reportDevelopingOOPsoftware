package com.scentstation.catalog.integration.machine.client;

import com.scentstation.catalog.integration.machine.dto.MachineAvailabilityInfo;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class LocalMachineAvailabilityClient
        implements MachineAvailabilityClient {

    // Fail closed trong local/test cho tới khi có Machine adapter thật.
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
                .available(false)
                .availableSlotCount(0)
                .build();
    }
}
