package com.scentstation.operation.inventory.movement.entity;

import com.scentstation.operation.inventory.movement.enums.BottleMovementType;
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
@Table(name = "bottle_movements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BottleMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "bottle_id", nullable = false)
    private Long bottleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private BottleMovementType movementType;

    @Column(name = "machine_id")
    private Long machineId;

    @Column(name = "slot_id")
    private Long slotId;

    @Column(
            name = "quantity_before",
            precision = 12,
            scale = 3
    )
    private BigDecimal quantityBefore;

    @Column(
            name = "quantity_after",
            precision = 12,
            scale = 3
    )
    private BigDecimal quantityAfter;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}