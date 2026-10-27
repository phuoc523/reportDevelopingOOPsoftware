package com.scentstation.identity.audit;

import com.scentstation.identity.auth.AuthDto;
import com.scentstation.identity.platform.InternalServiceKeys;
import com.scentstation.identity.rbac.Permission;
import com.scentstation.identity.rbac.RbacService;
import com.scentstation.identity.tenant.TenantScopeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.util.List;

@RestController
public class AuditController {
    private final AuditEventRepository repo;
    private final RbacService rbac;
    private final TenantScopeService scopes;
    private final Clock clock;
    private final String secret;
    public AuditController(AuditEventRepository repo,RbacService rbac,TenantScopeService scopes,Clock clock,
        @Value("${AUDIT_SERVICE_KEY:}") String secret){
        this.repo=repo;this.rbac=rbac;this.scopes=scopes;this.clock=clock;this.secret=secret;
    }
    public record AuditRequest(
        @NotBlank @Size(max=64) String tenantId,
        @NotBlank @Size(max=64) String actorId,
        @NotBlank @Size(max=80) String action,
        @NotBlank @Size(max=64) String resourceType,
        @NotBlank @Size(max=128) String resourceId,
        @NotBlank @Pattern(regexp="SUCCESS|DENIED|FAILED") String outcome,
        @NotBlank @Size(max=64) String requestId){}
    public record Ack(String auditId){}
    /** Trusted backend only; prohibit route at Gateway and protect network. */
    @PostMapping("/api/v1/internal/audit/events")
    public ResponseEntity<Ack> record(@RequestHeader(name="X-Internal-Service-Key",required=false) String supplied,
        @Valid @RequestBody AuditRequest request){
        InternalServiceKeys.verify(secret,supplied);
        AuditEvent saved=repo.save(new AuditEvent(request.tenantId(),request.actorId(),request.action(),
            request.resourceType(),request.resourceId(),request.outcome(),request.requestId(),clock.instant()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Ack(saved.getId()));
    }
    @GetMapping("/api/v1/audit/events")
    public ResponseEntity<List<AuditEvent>> list(Authentication authentication,
       @RequestParam(required=false) String tenantId){
        AuthDto.Identity actor=scopes.authenticated(authentication);
        String tenant=tenantId==null?actor.tenantId():tenantId;
        rbac.requirePermission(actor,Permission.ROLE_AUDIT_READ,tenant);
        var page=PageRequest.of(0,50);
        List<AuditEvent> result=tenant==null?repo.findAllByOrderByOccurredAtDesc(page):
            repo.findByTenantIdOrderByOccurredAtDesc(tenant,page);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
