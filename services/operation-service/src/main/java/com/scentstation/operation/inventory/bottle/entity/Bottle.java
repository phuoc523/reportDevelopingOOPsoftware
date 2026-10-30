package com.scentstation.operation.inventory.bottle.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "bottles",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_bottle_tenant_code",
                        columnNames = {"tenant_id", "bottle_code"}
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Bottle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "bottle_code", nullable = false, length = 100)
    private String bottleCode;

    @Column(
            name = "initial_quantity",
            nullable = false,
            precision = 12,
            scale = 3
    )
    private BigDecimal initialQuantity;

    @Column(
            name = "current_quantity",
            nullable = false,
            precision = 12,
            scale = 3
    )
    private BigDecimal currentQuantity;

    @Column(
            name = "calibrated_consumption_per_spray",
            precision = 12,
            scale = 3
    )
    private BigDecimal calibratedConsumptionPerSpray;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BottleStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime updatedAt;

    @PrePersist
    // Khởi tạo timestamp, quantity và status mặc định của bottle mới.
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (currentQuantity == null) {
            currentQuantity = initialQuantity;
        }

        if (status == null) {
            status = BottleStatus.AVAILABLE;
        }
    }

    @PreUpdate
    // Làm mới timestamp cập nhật trước khi persist thay đổi bottle.
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
