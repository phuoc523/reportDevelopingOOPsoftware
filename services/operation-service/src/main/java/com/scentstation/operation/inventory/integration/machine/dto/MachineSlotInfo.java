package com.scentstation.operation.inventory.integration.machine.dto;

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
public class MachineSlotInfo {

    private Long tenantId;

    private Long machineId;

    private Long slotId;

    private Long productId;

    private boolean active;

    private boolean maintenanceMode;
}