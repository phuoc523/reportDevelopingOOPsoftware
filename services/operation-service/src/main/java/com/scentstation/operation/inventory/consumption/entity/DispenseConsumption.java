package com.scentstation.operation.inventory.consumption.entity;

import com.scentstation.operation.inventory.consumption.enums.DispenseResult;
import com.scentstation.operation.inventory.consumption.enums.DispenseType;
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
        name = "dispense_consumptions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_dispense_tenant_command",
                        columnNames = {"tenant_id", "command_id"}
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DispenseConsumption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "command_id", nullable = false, length = 150)
    private String commandId;

    @Column(name = "bottle_id", nullable = false)
    private Long bottleId;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispense_type", nullable = false, length = 30)
    private DispenseType dispenseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 30)
    private DispenseResult result;

    @Column(
            name = "consumed_quantity",
            nullable = false,
            precision = 12,
            scale = 3
    )
    private BigDecimal consumedQuantity;

    @Column(name = "inventory_deducted", nullable = false)
    @Builder.Default
    private Boolean inventoryDeducted = false;

    @Column(name = "processed_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        processedAt = LocalDateTime.now();

        if (inventoryDeducted == null) {
            inventoryDeducted = false;
        }
    }

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
}