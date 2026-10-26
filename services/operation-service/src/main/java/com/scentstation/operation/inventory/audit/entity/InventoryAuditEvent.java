package com.scentstation.operation.inventory.audit.entity;

import com.scentstation.operation.inventory.audit.enums.InventoryAuditAction;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_audit_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InventoryAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "actor_id")
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 60)
    private InventoryAuditAction action;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "machine_id")
    private Long machineId;

    @Column(name = "slot_id")
    private Long slotId;

    @Column(name = "before_value", length = 1000)
    private String beforeValue;

    @Column(name = "after_value", length = 1000)
    private String afterValue;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "reference_id", length = 150)
    private String referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    @PrePersist
    // Gắn thời điểm tạo audit ngay trước khi entity được persist.
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
