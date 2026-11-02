package com.scentstation.operation.maintenance.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignMaintenanceRequest {

    @NotNull
    @Positive
    private Long tenantId;

    @NotNull
    @Positive
    private Long technicianActorId;

    @NotNull
    @Positive
    private Long assignedByActorId;
}