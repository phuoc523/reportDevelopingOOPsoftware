package com.scentstation.identity.rbac;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RoleAssignmentAuditRepository extends JpaRepository<RoleAssignmentAudit, String> {
    List<RoleAssignmentAudit> findTop50ByOrderByOccurredAtDesc();
    List<RoleAssignmentAudit> findTop50ByTargetTenantIdOrderByOccurredAtDesc(String targetTenantId);
}
