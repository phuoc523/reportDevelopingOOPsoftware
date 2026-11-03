package com.scentstation.operation.maintenance.repository;

import com.scentstation.operation.maintenance.entity.PostMaintenanceTest;
import com.scentstation.operation.maintenance.enums.PostMaintenanceTestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostMaintenanceTestRepository
        extends JpaRepository<PostMaintenanceTest, Long> {

    // Tìm post-maintenance test theo id trong đúng tenant.
    Optional<PostMaintenanceTest> findByIdAndTenantId(
            Long id,
            Long tenantId
    );

    // Lấy toàn bộ lịch sử post-maintenance test của ticket.
    List<PostMaintenanceTest>
    findAllByTenantIdAndTicketIdOrderByRequestedAtAsc(
            Long tenantId,
            Long ticketId
    );

    // Tìm test theo external id do Machine Service trả về.
    Optional<PostMaintenanceTest>
    findByTenantIdAndExternalTestId(
            Long tenantId,
            String externalTestId
    );

    // Kiểm tra ticket đã có post-maintenance test PASSED hay chưa.
    boolean existsByTenantIdAndTicketIdAndStatus(
            Long tenantId,
            Long ticketId,
            PostMaintenanceTestStatus status
    );

    // Lấy bài test mới nhất của ticket để không đóng ticket sau một kết quả FAILED mới hơn.
    Optional<PostMaintenanceTest>
    findFirstByTenantIdAndTicketIdOrderByRequestedAtDescIdDesc(
            Long tenantId,
            Long ticketId
    );
}
