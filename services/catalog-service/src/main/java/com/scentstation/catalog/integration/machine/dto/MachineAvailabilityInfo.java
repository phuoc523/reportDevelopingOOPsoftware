package com.scentstation.catalog.integration.machine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MachineAvailabilityInfo {

    private Long tenantId;
    private Long machineId;
    private Long productId;

    // Cho biết product hiện có thể được phục vụ trên machine hay không.
    private boolean available;

    // Số slot trên machine hiện có thể phục vụ product.
    private Integer availableSlotCount;
}