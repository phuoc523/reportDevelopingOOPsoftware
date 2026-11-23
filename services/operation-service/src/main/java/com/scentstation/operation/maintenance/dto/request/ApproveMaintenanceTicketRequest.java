package com.scentstation.operation.maintenance.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
public class ApproveMaintenanceTicketRequest {

    @NotNull(message = "tenantId is required")
    @Positive(message = "tenantId must be greater than 0")
    private Long tenantId;

    @NotNull(message = "approvedByActorId is required")
    @Positive(message = "approvedByActorId must be greater than 0")
    private Long approvedByActorId;

    @Size(max = 500, message = "note must not exceed 500 characters")
    private String note;
}
