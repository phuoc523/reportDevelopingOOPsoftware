package com.scentstation.catalog.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    private Long tenantId;

    @NotBlank
    private String name;

    private String description;

    private String fragranceNotes;

    private String imageUrl;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal defaultPrice;

    @Builder.Default
    private Boolean available = true;
}
