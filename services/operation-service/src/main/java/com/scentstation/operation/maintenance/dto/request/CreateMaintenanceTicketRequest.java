package com.scentstation.operation.maintenance.dto.request;

import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CreateMaintenanceTicketRequest {

    @NotNull
    @Positive
    private Long tenantId;

    @NotNull
    @Positive
    private Long machineId;

    @Positive
    private Long locationId;

    @Positive
    private Long slotId;

    @Positive
    private Long bottleId;

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotBlank
    private String category;

    @NotNull
    private MaintenanceSeverity severity;

    @NotNull
    private MaintenancePriority priority;

    @NotNull
    @Positive
    private Long reportedByActorId;

    private LocalDateTime dueAt;

    private boolean critical;
}