package com.scentstation.order.client;

public interface CatalogServiceGateway {
    CatalogProductSnapshot getProduct(long tenantId, long productId);

    default void commitBottleSale(long tenantId, long productId, long orderId, int quantity) { }
}
