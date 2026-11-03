package com.scentstation.operation.maintenance.entity;

import com.scentstation.operation.maintenance.enums.MaintenanceAuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_audit_events")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MaintenanceAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 60)
    private MaintenanceAuditAction action;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "machine_id")
    private Long machineId;

    @Column(name = "before_state", length = 2000)
    private String beforeState;

    @Column(name = "after_state", length = 2000)
    private String afterState;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Gắn thời điểm tạo một lần khi audit event được persist.
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
