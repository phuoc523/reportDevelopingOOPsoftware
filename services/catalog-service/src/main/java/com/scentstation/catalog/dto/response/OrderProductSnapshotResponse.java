package com.scentstation.catalog.dto.response;

import java.math.BigDecimal;

public record OrderProductSnapshotResponse(
        Long productId,
        Long tenantId,
        String name,
        Boolean available,
        BigDecimal unitPrice,
        Long campaignId,
        BigDecimal bottlePrice,
        Integer bottleVolumeMl,
        Integer bottleStock
) {
    public OrderProductSnapshotResponse(Long productId, Long tenantId, String name, Boolean available,
                                        BigDecimal unitPrice, Long campaignId) {
        this(productId, tenantId, name, available, unitPrice, campaignId, null, null, 0);
    }
}
