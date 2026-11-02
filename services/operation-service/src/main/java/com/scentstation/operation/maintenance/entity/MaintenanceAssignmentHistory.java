package com.scentstation.operation.maintenance.entity;

import com.scentstation.operation.maintenance.enums.MaintenanceAssignmentEventType;
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
        name = "maintenance_assignment_history",
        indexes = {
                @Index(
                        name = "idx_maintenance_assignment_ticket",
                        columnList = "tenant_id,ticket_id"
                ),
                @Index(
                        name = "idx_maintenance_assignment_technician",
                        columnList = "tenant_id,technician_actor_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MaintenanceAssignmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "previous_technician_actor_id")
    private Long previousTechnicianActorId;

    @Column(name = "technician_actor_id", nullable = false)
    private Long technicianActorId;

    @Column(name = "performed_by_actor_id", nullable = false)
    private Long performedByActorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private MaintenanceAssignmentEventType eventType;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    // Ghi timestamp khi phát sinh sự kiện phân công hoặc xác nhận kỹ thuật viên.
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}