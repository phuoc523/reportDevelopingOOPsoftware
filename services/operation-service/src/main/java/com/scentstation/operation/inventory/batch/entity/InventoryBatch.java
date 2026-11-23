package com.scentstation.operation.inventory.batch.entity;

import com.scentstation.operation.inventory.batch.enums.InventoryBatchStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_batches", uniqueConstraints = {
    @UniqueConstraint(name = "uk_inventory_batch_tenant_code", columnNames = {"tenant_id", "batch_code"})
        }
    )
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InventoryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "batch_code", nullable = false, length = 100)
    private String batchCode;

    @Column(name = "received_quantity", nullable = false)
    private Integer receivedQuantity;

    @Column(name = "received_at", nullable = false)
    private LocalDate receivedAt;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InventoryBatchStatus status;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime updatedAt;

    @PrePersist
    // Khởi tạo timestamp tạo và cập nhật khi batch được persist lần đầu.
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = InventoryBatchStatus.ACTIVE;
        }
    }

    @PreUpdate
    // Làm mới timestamp cập nhật trước khi ghi thay đổi batch.
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
