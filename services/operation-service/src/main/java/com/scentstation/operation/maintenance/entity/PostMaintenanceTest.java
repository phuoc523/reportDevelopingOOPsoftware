package com.scentstation.operation.maintenance.entity;

import com.scentstation.operation.maintenance.enums.PostMaintenanceTestStatus;
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
        name = "post_maintenance_tests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_post_maintenance_test_external",
                columnNames = {"tenant_id", "external_test_id"}
        ),
        indexes = @Index(
                name = "idx_post_maintenance_test_ticket",
                columnList = "tenant_id,ticket_id"
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PostMaintenanceTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "requested_by_actor_id", nullable = false)
    private Long requestedByActorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PostMaintenanceTestStatus status;

    @Column(name = "external_test_id", length = 150)
    private String externalTestId;

    @Column(name = "result_message", length = 2000)
    private String resultMessage;

    @Column(name = "requested_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // Khởi tạo thời điểm yêu cầu và trạng thái mặc định của bài test.
    @PrePersist
    protected void onCreate() {
        requestedAt = LocalDateTime.now();
        if (status == null) {
            status = PostMaintenanceTestStatus.REQUESTED;
        }
    }
}
