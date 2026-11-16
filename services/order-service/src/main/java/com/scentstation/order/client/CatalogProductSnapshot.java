package com.scentstation.order.client;

import java.math.BigDecimal;

public record CatalogProductSnapshot(Long productId, Long tenantId, String name, Boolean available,
                                     BigDecimal unitPrice, Long campaignId) {}
