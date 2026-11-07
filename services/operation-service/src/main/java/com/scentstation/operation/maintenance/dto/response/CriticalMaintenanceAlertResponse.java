package com.scentstation.operation.maintenance.dto.response;

import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CriticalMaintenanceAlertResponse {

    private String alertId;

    private Long ticketId;

    private Long tenantId;

    private Long machineId;

    private MaintenanceSeverity severity;

    private MaintenancePriority priority;

    private MaintenanceStatus status;
}