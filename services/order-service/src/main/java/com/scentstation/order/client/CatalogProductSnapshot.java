package com.scentstation.order.client;

import java.math.BigDecimal;

public record CatalogProductSnapshot(Long productId, Long tenantId, String name, Boolean available,
                                     BigDecimal unitPrice, Long campaignId, BigDecimal bottlePrice,
                                     Integer bottleVolumeMl, Integer bottleStock) {
    public CatalogProductSnapshot(Long productId, Long tenantId, String name, Boolean available,
                                  BigDecimal unitPrice, Long campaignId) {
        this(productId, tenantId, name, available, unitPrice, campaignId, null, null, 0);
    }
}
