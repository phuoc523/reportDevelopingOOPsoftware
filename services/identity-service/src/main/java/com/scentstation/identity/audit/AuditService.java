package com.scentstation.identity.audit;

import com.scentstation.identity.rbac.AccessPolicy;
import com.scentstation.identity.rbac.AccountPrincipal;
import com.scentstation.identity.rbac.Permission;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuditService {
    private final AuditRepository repository;
    private final AccessPolicy access;

    public AuditService(AuditRepository repository, AccessPolicy access) {
        this.repository = repository;
        this.access = access;
    }

    /** Never pass passwords, tokens or secrets in before/after values. */
    public void record(Long actorId, Long tenantId, String action, String type,
                       String targetId, String before, String after) {
        repository.save(new AuditEvent(actorId, tenantId, action, type, targetId, "SUCCESS", before, after));
    }

    public void authAttempt(Long actorId, Long tenantId, String username, boolean successful) {
        // Never log raw credentials; usernames here are deliberately omitted from targetId.
        repository.save(new AuditEvent(actorId, tenantId, "LOGIN", "USER", actorId == null ? "unknown" : actorId.toString(),
            successful ? "SUCCESS" : "FAILURE", null, null));
    }

    public List<AuditEvent> recent(Long requestedTenantId) {
        AccountPrincipal a = access.current();
        boolean global = access.platform();
        if (global) {
            if (requestedTenantId == null) return repository.findAll(PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC,"id"))).getContent();
            return repository.findByTenantId(requestedTenantId, PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC,"id")));
        }
        if (!access.has(Permission.AUDIT_READ) || a.tenantId() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Audit access denied");
        if (requestedTenantId != null && !a.tenantId().equals(requestedTenantId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access denied");
        return repository.findByTenantId(a.tenantId(), PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC,"id")));
    }
}
