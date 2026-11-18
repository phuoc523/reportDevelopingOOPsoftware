package com.scentstation.catalog.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateProductRequest {

    @NotBlank
    private String name;

    private String description;

    private String fragranceNotes;

    private String imageUrl;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal defaultPrice;

    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal bottlePrice;

    @Positive
    private Integer bottleVolumeMl;

    @PositiveOrZero
    private Integer bottleStock;

    @NotNull
    private Boolean available;

}
