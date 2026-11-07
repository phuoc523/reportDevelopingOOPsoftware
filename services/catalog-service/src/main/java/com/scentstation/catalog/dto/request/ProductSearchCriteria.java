package com.scentstation.catalog.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductSearchCriteria {

    private Long tenantId;

    private String keyword;

    private Boolean available;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;
}
