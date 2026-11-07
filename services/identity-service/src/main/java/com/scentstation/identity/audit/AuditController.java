package com.scentstation.identity.audit;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService audit;
    public AuditController(AuditService audit) { this.audit = audit; }

    @GetMapping("/recent")
    @PreAuthorize("@accessPolicy.has('AUDIT_READ') or @accessPolicy.platform()")
    public List<AuditEvent> recent(@RequestParam(required = false) Long tenantId) {
        return audit.recent(tenantId);
    }
}
