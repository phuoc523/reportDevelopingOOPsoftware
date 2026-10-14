package com.scentstation.catalog.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class ProductResponse {

    private Long id;
    private Long tenantId;
    private String name;
    private String description;
    private String fragranceNotes;
    private String imageUrl;
    private BigDecimal defaultPrice;
    private Boolean available;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
