package com.scentstation.operation.maintenance.repository;

import com.scentstation.operation.maintenance.entity.MaintenanceRepairLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceRepairLogRepository
        extends JpaRepository<MaintenanceRepairLog, Long> {

    // Tìm repair log theo id trong đúng tenant.
    Optional<MaintenanceRepairLog> findByIdAndTenantId(
            Long id,
            Long tenantId
    );

    // Lấy toàn bộ repair log của một maintenance ticket theo thời gian.
    List<MaintenanceRepairLog>
    findAllByTenantIdAndTicketIdOrderByCreatedAtAsc(
            Long tenantId,
            Long ticketId
    );

    // Kiểm tra maintenance ticket đã có ít nhất một repair result hay chưa.
    boolean existsByTenantIdAndTicketId(
            Long tenantId,
            Long ticketId
    );
}