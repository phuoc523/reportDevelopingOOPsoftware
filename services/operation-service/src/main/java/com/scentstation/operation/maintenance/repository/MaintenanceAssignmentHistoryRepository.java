package com.scentstation.operation.maintenance.repository;

import com.scentstation.operation.maintenance.entity.MaintenanceAssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceAssignmentHistoryRepository
        extends JpaRepository<MaintenanceAssignmentHistory, Long> {

    // Lấy lịch sử phân công của một ticket theo thứ tự thời gian.
    List<MaintenanceAssignmentHistory>
    findAllByTenantIdAndTicketIdOrderByCreatedAtAsc(
            Long tenantId,
            Long ticketId
    );

    // Lấy assignment history theo thời gian tăng dần với id làm tie-breaker.
    List<MaintenanceAssignmentHistory>
    findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(
            Long tenantId,
            Long ticketId
    );

    // Lấy lịch sử công việc đã được phân cho một technician trong tenant.
    List<MaintenanceAssignmentHistory>
    findAllByTenantIdAndTechnicianActorIdOrderByCreatedAtDesc(
            Long tenantId,
            Long technicianActorId
    );
}
