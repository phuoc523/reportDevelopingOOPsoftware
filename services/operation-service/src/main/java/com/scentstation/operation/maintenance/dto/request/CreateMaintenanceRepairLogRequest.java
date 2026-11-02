package com.scentstation.operation.maintenance.dto.request;

import com.scentstation.operation.maintenance.enums.MaintenanceRepairResult;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateMaintenanceRepairLogRequest {

    @NotNull
    @Positive
    private Long tenantId;

    @NotNull
    @Positive
    private Long diagnosedByActorId;

    @NotBlank
    private String diagnosticResult;

    @NotBlank
    private String correctiveAction;

    private String replacedPart;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal cost;

    @NotNull
    private MaintenanceRepairResult repairResult;

    private String note;

    private String evidenceUrl;
}