package com.scentstation.operation.inventory.installation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "bottle_installations")
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

    @Column(name = "active", nullable = false)
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
