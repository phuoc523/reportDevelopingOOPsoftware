package com.scentstation.operation.maintenance.repository;

import com.scentstation.operation.maintenance.entity.MaintenanceChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceChecklistRepository
        extends JpaRepository<MaintenanceChecklistItem, Long> {

    // Tìm checklist item theo id trong đúng tenant.
    Optional<MaintenanceChecklistItem> findByIdAndTenantId(
            Long id,
            Long tenantId
    );

    // Lấy toàn bộ checklist của một maintenance ticket trong tenant.
    List<MaintenanceChecklistItem>
    findAllByTenantIdAndTicketIdOrderByIdAsc(
            Long tenantId,
            Long ticketId
    );

    // Kiểm tra ticket còn checklist bắt buộc chưa hoàn thành hay không.
    boolean existsByTenantIdAndTicketIdAndRequiredTrueAndCompletedFalse(
            Long tenantId,
            Long ticketId
    );

    // Đếm checklist item của ticket.
    long countByTenantIdAndTicketId(
            Long tenantId,
            Long ticketId
    );
}