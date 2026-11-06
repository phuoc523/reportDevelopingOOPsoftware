package com.scentstation.identity.rbac;

import com.scentstation.identity.auth.AuthDto;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/rbac")
public class RbacController {
    private final RbacService rbac;

    public RbacController(RbacService rbac) { this.rbac = rbac; }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        AuthDto.Identity actor = actor(auth);
        Set<String> permissions = RbacPolicy.permissionsFor(actor.roles()).stream()
                .map(Enum::name).collect(Collectors.toUnmodifiableSet());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("userId", actor.userId(), "roles", actor.roles(), "permissions", permissions));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('PERM_ROLE_READ')")
    public ResponseEntity<?> catalog() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(RbacPolicy.catalog());
    }

    /** Policy decision only. Does NOT execute the actual sensitive operation. */
    @GetMapping("/check/{permission}")
    public ResponseEntity<Void> check(@PathVariable Permission permission,
                                      @RequestParam(required = false) String tenantId,
                                      Authentication auth) {
        AuthDto.Identity actor = actor(auth);
        rbac.requirePermission(actor, permission, tenantId == null ? actor.tenantId() : tenantId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PutMapping("/users/{userId}/roles/{role}")
    @PreAuthorize("hasAuthority('PERM_ROLE_ASSIGN')")
    public ResponseEntity<RbacService.RoleUpdateResponse> grant(@PathVariable String userId,
                                                                 @PathVariable Role role,
                                                                 Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(rbac.grant(actor(auth), userId, role));
    }

    @DeleteMapping("/users/{userId}/roles/{role}")
    @PreAuthorize("hasAuthority('PERM_ROLE_REVOKE')")
    public ResponseEntity<RbacService.RoleUpdateResponse> revoke(@PathVariable String userId,
                                                                  @PathVariable Role role,
                                                                  Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(rbac.revoke(actor(auth), userId, role));
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('PERM_ROLE_AUDIT_READ')")
    public ResponseEntity<?> audit(Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rbac.audit(actor(auth)));
    }

    private AuthDto.Identity actor(Authentication auth) {
        return (AuthDto.Identity) auth.getPrincipal();
    }
}
