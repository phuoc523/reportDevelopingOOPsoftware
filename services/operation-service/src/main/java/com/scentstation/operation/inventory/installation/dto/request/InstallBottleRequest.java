package com.scentstation.operation.inventory.installation.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

@Getter 
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
@Builder 
public class InstallBottleRequest {
    @NotNull 
    @Positive 
    private Long tenantId;

    @NotNull 
    @Positive 
    private Long bottleId;

    @NotNull 
    @Positive 
    private Long machineId;

    @NotNull 
    @Positive
    private Long slotId;

    @NotNull 
    @Positive
    private Long actorId;
}
