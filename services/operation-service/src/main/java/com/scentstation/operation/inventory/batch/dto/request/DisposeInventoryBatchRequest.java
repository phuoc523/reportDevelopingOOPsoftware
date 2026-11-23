package com.scentstation.operation.inventory.batch.dto.request;

import jakarta.validation.constraints.NotBlank;
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
public class DisposeInventoryBatchRequest {

    @NotNull(message = "tenantId is required")
    @Positive(message = "tenantId must be greater than 0")
    private Long tenantId;

    @NotNull(message = "actorId is required")
    @Positive(message = "actorId must be greater than 0")
    private Long actorId;

    @NotBlank(message = "reason is required")
    @Size(max = 500, message = "reason must not exceed 500 characters")
    private String reason;
}
