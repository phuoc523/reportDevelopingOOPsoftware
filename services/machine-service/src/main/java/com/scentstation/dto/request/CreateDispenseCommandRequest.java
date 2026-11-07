package com.scentstation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDispenseCommandRequest(
    @NotBlank @Size(max = 100) String tenantId,
    @NotBlank @Size(max = 100) String orderId,
    @NotBlank @Size(max = 100) String paymentReference,
    @NotBlank @Size(max = 100) String machineId,
    @NotBlank @Size(max = 100) String slotId,
    @NotBlank @Size(max = 100) String commandId
) {
}
