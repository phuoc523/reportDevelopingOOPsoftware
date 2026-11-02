package com.scentstation.operation.maintenance.dto.response;

import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MaintenanceTicketResponse {

    private Long id;

    private Long tenantId;

    private Long machineId;

    private Long locationId;

    private Long slotId;

    private Long bottleId;

    private String title;

    private String description;

    private String category;

    private MaintenanceSeverity severity;

    private MaintenancePriority priority;

    private MaintenanceStatus status;

    private Long reportedByActorId;

    private Long assignedToActorId;

    private LocalDateTime dueAt;

    private boolean critical;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime closedAt;
}