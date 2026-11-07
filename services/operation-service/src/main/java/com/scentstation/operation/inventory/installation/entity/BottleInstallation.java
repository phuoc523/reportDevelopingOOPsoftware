package com.scentstation.operation.inventory.installation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Table(
        name = "bottle_installations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_active_installation_slot",
                        columnNames = {"tenant_id", "machine_id", "slot_id", "active"}
                ),
                @UniqueConstraint(
                        name = "uk_active_installation_bottle",
                        columnNames = {"tenant_id", "bottle_id", "active"}
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BottleInstallation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "bottle_id", nullable = false)
    private Long bottleId;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "installed_by")
    private Long installedBy;

    @Column(name = "installed_at", nullable = false)
    private LocalDateTime installedAt;

    @Column(name = "removed_by")
    private Long removedBy;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @Column(name = "quantity_before_installation", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityBeforeInstallation;

    @Column(name = "quantity_after_removal", precision = 12, scale = 3)
    private BigDecimal quantityAfterRemoval;

    /*
     * TRUE đánh dấu installation đang hoạt động; NULL cho phép lưu nhiều
     * bản ghi lịch sử trong khi unique constraint chặn hai bản ghi active.
     */
    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    @PrePersist
    // Khởi tạo thời điểm lắp và trạng thái active trước khi persist.
    protected void onCreate() {
        if (installedAt == null) {
            installedAt = LocalDateTime.now();
        }

        if (active == null) {
            active = true;
        }
    }
}
