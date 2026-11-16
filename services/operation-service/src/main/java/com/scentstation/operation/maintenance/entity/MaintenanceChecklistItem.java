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
        name = "maintenance_checklist_items",
        indexes = {
                @Index(
                        name = "idx_maintenance_checklist_ticket",
                        columnList = "tenant_id,ticket_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MaintenanceChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "required_item", nullable = false)
    private boolean required;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "result_note", length = 2000)
    private String resultNote;

    @Column(name = "completed_by_actor_id")
    private Long completedByActorId;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    // Khởi tạo timestamp và trạng thái mặc định của checklist item.
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}