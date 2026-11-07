package com.scentstation.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "product_name", nullable = false)
    private String productName;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotal;

    protected OrderItem() {
    }

    public OrderItem(Long productId, String productName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineTotal = lineTotal;
    }

    void setOrder(Order order) {
        this.order = order;
    }

    public Long productId() {
        return productId;
    }

    public String productName() {
        return productName;
    }

    public BigDecimal unitPrice() {
        return unitPrice;
    }

    public int quantity() {
        return quantity;
    }

    public BigDecimal lineTotal() {
        return lineTotal;
    }
}
