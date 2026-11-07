package com.scentstation.operation.maintenance.entity;

import com.scentstation.operation.maintenance.enums.MaintenanceRepairResult;
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
        name = "maintenance_repair_logs",
        indexes = {
                @Index(
                        name = "idx_repair_log_ticket",
                        columnList = "tenant_id,ticket_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MaintenanceRepairLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "diagnosed_by_actor_id", nullable = false)
    private Long diagnosedByActorId;

    @Column(name = "diagnostic_result", nullable = false, length = 2000)
    private String diagnosticResult;

    @Column(name = "corrective_action", nullable = false, length = 2000)
    private String correctiveAction;

    @Column(name = "replaced_part", length = 500)
    private String replacedPart;

    @Column(
            name = "cost",
            precision = 15,
            scale = 2
    )
    private BigDecimal cost;

    @Enumerated(EnumType.STRING)
    @Column(name = "repair_result", nullable = false, length = 30)
    private MaintenanceRepairResult repairResult;

    @Column(name = "note", length = 2000)
    private String note;

    @Column(name = "evidence_url", length = 1000)
    private String evidenceUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    // Ghi timestamp bất biến khi tạo repair log.
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}