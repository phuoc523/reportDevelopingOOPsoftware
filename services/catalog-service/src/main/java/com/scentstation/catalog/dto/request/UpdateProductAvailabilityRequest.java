package com.scentstation.catalog.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductAvailabilityRequest {

    @NotNull
    private Boolean available;
}