package com.scentstation.identity.audit;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByTenantId(Long tenantId, Pageable pageable);
}
