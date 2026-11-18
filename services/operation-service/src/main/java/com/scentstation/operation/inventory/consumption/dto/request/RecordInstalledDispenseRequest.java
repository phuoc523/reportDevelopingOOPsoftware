package com.scentstation.operation.inventory.consumption.dto.request;

import com.scentstation.operation.inventory.consumption.enums.DispenseResult;
import com.scentstation.operation.inventory.consumption.enums.DispenseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class RecordInstalledDispenseRequest {
    @NotNull @Positive
    private Long tenantId;
    @NotBlank
    private String commandId;
    @NotNull @Positive
    private Long machineId;
    @NotNull @Positive
    private Long slotId;
    @NotNull
    private DispenseType dispenseType;
    @NotNull
    private DispenseResult result;
}
