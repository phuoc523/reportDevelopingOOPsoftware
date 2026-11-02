package com.scentstation.operation.maintenance.repository;

import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceTicketRepository
        extends JpaRepository<MaintenanceTicket, Long> {

    // Tìm maintenance ticket theo id trong đúng tenant.
    Optional<MaintenanceTicket> findByIdAndTenantId(
            Long id,
            Long tenantId
    );

    // Lấy toàn bộ maintenance ticket của một tenant.
    List<MaintenanceTicket> findAllByTenantId(
            Long tenantId
    );

    // Lấy maintenance ticket theo tenant và trạng thái.
    List<MaintenanceTicket> findAllByTenantIdAndStatus(
            Long tenantId,
            MaintenanceStatus status
    );

    // Lấy lịch sử maintenance của một machine trong tenant.
    List<MaintenanceTicket> findAllByTenantIdAndMachineId(
            Long tenantId,
            Long machineId
    );

    // Lấy maintenance ticket của một kỹ thuật viên được phân công.
    List<MaintenanceTicket> findAllByTenantIdAndAssignedToActorId(
            Long tenantId,
            Long assignedToActorId
    );

    // Kiểm tra machine có maintenance ticket chưa hoàn tất theo trạng thái cụ thể hay không.
    boolean existsByTenantIdAndMachineIdAndStatus(
            Long tenantId,
            Long machineId,
            MaintenanceStatus status
    );
}