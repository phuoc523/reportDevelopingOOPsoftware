package com.scentstation.catalog.dto.response;

import com.scentstation.catalog.enums.DiscountType;
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
public class CampaignResponse {

    private Long id;
    private Long tenantId;
    private Long productId;
    private String name;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}