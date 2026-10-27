package com.scentstation.identity.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent,String>{
    List<AuditEvent> findByTenantIdOrderByOccurredAtDesc(String tenantId,Pageable page);
    List<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable page);
}
