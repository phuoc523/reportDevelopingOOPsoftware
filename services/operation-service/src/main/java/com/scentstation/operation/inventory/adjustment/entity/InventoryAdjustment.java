package com.scentstation.operation.inventory.adjustment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_adjustments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InventoryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "bottle_id", nullable = false)
    private Long bottleId;

    @Column(
            name = "quantity_before",
            nullable = false,
            precision = 12,
            scale = 3
    )
    private BigDecimal quantityBefore;

    @Column(
            name = "quantity_after",
            nullable = false,
            precision = 12,
            scale = 3
    )
    private BigDecimal quantityAfter;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    @PrePersist
    // Gắn thời điểm adjustment được tạo trước khi persist.
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
