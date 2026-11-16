package com.scentstation.catalog.integration.machine.client;

import com.scentstation.catalog.integration.machine.dto.MachineAvailabilityInfo;

public interface MachineAvailabilityClient {

    // Lấy trạng thái khả dụng của product trên một machine thuộc tenant.
    MachineAvailabilityInfo getProductAvailability(
            Long tenantId,
            Long machineId,
            Long productId
    );
}