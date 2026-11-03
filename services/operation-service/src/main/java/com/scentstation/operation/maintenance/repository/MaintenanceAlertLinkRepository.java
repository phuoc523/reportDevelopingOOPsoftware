package com.scentstation.operation.maintenance.repository;

import com.scentstation.operation.maintenance.entity.MaintenanceAlertLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MaintenanceAlertLinkRepository
        extends JpaRepository<MaintenanceAlertLink, Long> {

    // Kiểm tra critical alert đã được chuyển thành maintenance ticket hay chưa.
    boolean existsByTenantIdAndSourceAlertId(
            Long tenantId,
            String sourceAlertId
    );

    // Tìm liên kết alert trong đúng tenant.
    Optional<MaintenanceAlertLink> findByTenantIdAndSourceAlertId(
            Long tenantId,
            String sourceAlertId
    );
}