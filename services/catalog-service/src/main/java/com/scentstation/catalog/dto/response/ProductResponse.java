package com.scentstation.catalog.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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