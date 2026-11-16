package com.scentstation.operation.inventory.reconciliation.entity;

import com.scentstation.operation.inventory.reconciliation.enums.ReconciliationStatus;
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
@Table(
        name = "sensor_reconciliations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_reconciliation_tenant_event",
                        columnNames = {"tenant_id", "sensor_event_id"}
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SensorReconciliation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "sensor_event_id", nullable = false, length = 150)
    private String sensorEventId;

    @Column(name = "bottle_id", nullable = false)
    private Long bottleId;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "estimated_remaining_sprays")
    private Long estimatedRemainingSprays;
    
    @Column(
            name = "predicted_quantity",
            nullable = false,
            precision = 12,
            scale = 3
    )
    private BigDecimal predictedQuantity;

    @Column(
            name = "sensor_quantity",
            nullable = false,
            precision = 12,
            scale = 3
    )
    private BigDecimal sensorQuantity;

    @Column(
            name = "discrepancy",
            nullable = false,
            precision = 12,
            scale = 3
    )
    private BigDecimal discrepancy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReconciliationStatus status;

    @Column(name = "low_stock", nullable = false)
    private Boolean lowStock;

    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    @Column(name = "processed_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime processedAt;

    @PrePersist
    // Gắn thời điểm reconciliation được xử lý trước khi persist.
    protected void onCreate() {
        processedAt = LocalDateTime.now();
    }
}
