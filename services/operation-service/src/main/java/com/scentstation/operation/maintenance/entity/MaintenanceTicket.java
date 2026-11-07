package com.scentstation.operation.maintenance.entity;

import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
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
        name = "maintenance_tickets",
        indexes = {
                @Index(
                        name = "idx_maintenance_tenant_status",
                        columnList = "tenant_id,status"
                ),
                @Index(
                        name = "idx_maintenance_tenant_machine",
                        columnList = "tenant_id,machine_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MaintenanceTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "slot_id")
    private Long slotId;

    @Column(name = "bottle_id")
    private Long bottleId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 30)
    private MaintenanceSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 30)
    private MaintenancePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MaintenanceStatus status;

    @Column(name = "reported_by_actor_id", nullable = false)
    private Long reportedByActorId;

    @Column(name = "assigned_to_actor_id")
    private Long assignedToActorId;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "critical", nullable = false)
    private boolean critical;    
    // Khởi tạo trạng thái và timestamp mặc định khi tạo maintenance ticket mới.
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (status == null) {
            status = MaintenanceStatus.OPEN;
        }

        if (priority == null) {
            priority = MaintenancePriority.MEDIUM;
        }

        if (severity == null) {
            severity = MaintenanceSeverity.MODERATE;
        }
    }

    // Cập nhật timestamp mỗi khi maintenance ticket được thay đổi.
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}