package com.scentstation.order.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class Order {

    private Long id;
    private String tenantId;
    private final Long customerId;
    private final String machineId;
    private final String slotId;
    private final List<OrderItem> items;
    private final BigDecimal totalAmount;
    private final String currency;
    private LocalDateTime expiresAt;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Order(Long customerId, String machineId, String slotId, List<OrderItem> items, BigDecimal totalAmount) {
        this("TENANT-001", customerId, machineId, slotId, items, totalAmount, "VND",
                LocalDateTime.now().plusMinutes(15));
    }

    public Order(
            String tenantId,
            Long customerId,
            String machineId,
            String slotId,
            List<OrderItem> items,
            BigDecimal totalAmount,
            String currency,
            LocalDateTime expiresAt
    ) {
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.machineId = machineId;
        this.slotId = slotId;
        this.items = List.copyOf(items);
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getMachineId() {
        return machineId;
    }

    public String getSlotId() {
        return slotId;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
