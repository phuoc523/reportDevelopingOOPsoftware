package com.scentstation.catalog.dto.response;

import com.scentstation.catalog.enums.DiscountType;
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
public class EffectivePriceResponse {

    private Long tenantId;
    private Long productId;

    private BigDecimal defaultPrice;
    private BigDecimal effectivePrice;

    private Long campaignId;
    private String campaignName;

    private DiscountType discountType;
    private BigDecimal discountValue;

    private Boolean promotionApplied;
}