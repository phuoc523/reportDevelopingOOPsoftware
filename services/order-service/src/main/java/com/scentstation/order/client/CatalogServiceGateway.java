package com.scentstation.order.client;

public interface CatalogServiceGateway {
    CatalogProductSnapshot getProduct(long tenantId, long productId);
}
