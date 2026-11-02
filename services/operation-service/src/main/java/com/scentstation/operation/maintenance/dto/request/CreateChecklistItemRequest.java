package com.scentstation.operation.maintenance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateChecklistItemRequest {

    @NotNull
    @Positive
    private Long tenantId;

    @NotBlank
    private String itemName;

    private String description;

    private boolean required;
}