package com.scentstation.catalog.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductSearchCriteria {

    @NotNull
    @Positive
    private Long tenantId;

    private String keyword;

    private Boolean available;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;
}