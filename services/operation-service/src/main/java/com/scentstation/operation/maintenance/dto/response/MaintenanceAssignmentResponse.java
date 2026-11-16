package com.scentstation.operation.maintenance.dto.response;

import com.scentstation.operation.maintenance.enums.MaintenanceAssignmentEventType;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MaintenanceAssignmentResponse {

    private Long ticketId;

    private Long tenantId;

    private Long previousTechnicianActorId;

    private Long technicianActorId;

    private Long performedByActorId;

    private MaintenanceAssignmentEventType eventType;

    private MaintenanceStatus ticketStatus;

    private LocalDateTime eventTime;
}