package com.scentstation.operation.maintenance.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "maintenance_alert_links",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_maintenance_alert_tenant_alert",
                        columnNames = {
                                "tenant_id",
                                "source_alert_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_maintenance_alert_ticket",
                        columnList = "tenant_id,ticket_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MaintenanceAlertLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "source_alert_id", nullable = false, length = 150)
    private String sourceAlertId;

    @Column(name = "alert_type", nullable = false, length = 100)
    private String alertType;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    // Ghi timestamp khi alert được liên kết với maintenance ticket.
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}