package com.scentstation.operation.maintenance.dto.request;

import com.scentstation.operation.maintenance.enums.PostMaintenanceTestStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostMaintenanceTestResultRequest {

    @NotNull
    @Positive
    private Long tenantId;

    @NotBlank
    private String externalTestId;

    @NotNull
    private PostMaintenanceTestStatus status;

    private String resultMessage;
}
