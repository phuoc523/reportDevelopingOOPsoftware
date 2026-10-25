package com.scentstation.operation.inventory.installation.dto.request;

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
public class ReplaceBottleRequest {

    @NotNull
    @Positive
    private Long tenantId;

    @NotNull
    @Positive
    private Long oldBottleId;

    @NotNull
    @Positive
    private Long newBottleId;

    @NotNull
    @Positive
    private Long actorId;

    @Size(max = 500)
    private String reason;
}