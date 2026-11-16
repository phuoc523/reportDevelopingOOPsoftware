package com.scentstation.operation.maintenance.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MaintenanceMachineHistoryResponse {

    private Long tenantId;
    private Long machineId;
    private List<MaintenanceTicketResponse> tickets;
}
