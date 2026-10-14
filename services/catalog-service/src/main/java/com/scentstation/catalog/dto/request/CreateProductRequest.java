package com.scentstation.catalog.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateProductRequest {

    @NotNull
    private Long tenantId;

    @NotBlank
    private String name;

    private String description;

    private String fragranceNotes;

    private String imageUrl;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal defaultPrice;

    private Boolean available = true;

}
