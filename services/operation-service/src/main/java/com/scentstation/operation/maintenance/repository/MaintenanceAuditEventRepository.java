package com.scentstation.operation.maintenance.repository;

import com.scentstation.operation.maintenance.entity.MaintenanceAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceAuditEventRepository
        extends JpaRepository<MaintenanceAuditEvent, Long> {

    // Trả audit nội bộ của ticket theo đúng tenant và thứ tự phát sinh.
    List<MaintenanceAuditEvent> findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(
            Long tenantId,
            Long ticketId
    );
}
