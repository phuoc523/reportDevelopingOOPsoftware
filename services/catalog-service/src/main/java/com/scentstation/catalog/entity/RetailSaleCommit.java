package com.scentstation.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "retail_sale_commits")
public class RetailSaleCommit {
    @Id
    @Column(name = "order_id")
    private Long orderId;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(nullable = false)
    private Integer quantity;
    @Column(name = "committed_at", nullable = false, updatable = false)
    private LocalDateTime committedAt;

    protected RetailSaleCommit() { }

    public RetailSaleCommit(Long orderId, Long tenantId, Long productId, Integer quantity) {
        this.orderId = orderId;
        this.tenantId = tenantId;
        this.productId = productId;
        this.quantity = quantity;
    }

    @PrePersist
    void onCreate() { committedAt = LocalDateTime.now(); }
}
