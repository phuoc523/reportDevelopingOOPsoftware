package com.scentstation.catalog.dto.response;

import java.math.BigDecimal;

public record OrderProductSnapshotResponse(
        Long productId,
        Long tenantId,
        String name,
        Boolean available,
        BigDecimal unitPrice,
        Long campaignId
) {}
