package com.scentstation.identity.tenant;

import com.scentstation.identity.auth.AuthDto;
import com.scentstation.identity.model.StaffUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/tenant-scope")
public class TenantScopeController {
    private final TenantScopeService scopes;

    public TenantScopeController(TenantScopeService scopes) { this.scopes = scopes; }

    public record TenantIdentity(String userId, String username, String tenantId, Set<String> roles) {}
    public record TenantRequest(@NotBlank String tenantId) {}
    public record UserSummary(String userId, String username, String tenantId, Set<String> roles) {}

    @GetMapping("/me")
    public ResponseEntity<TenantIdentity> me(Authentication auth,
               @RequestHeader(name = "X-Tenant-Id", required = false) String hint,
               @RequestParam(name = "tenantId", required = false) String queryHint) {
        AuthDto.Identity actor = scopes.authenticated(auth);
        scopes.requireMatchingHints(actor, hint, queryHint);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new TenantIdentity(actor.userId(), actor.username(), actor.tenantId(), actor.roles()));
    }

    @GetMapping("/check")
    public ResponseEntity<Void> check(Authentication auth,
                       @RequestParam("tenantId") String target,
                       @RequestHeader(name = "X-Tenant-Id", required = false) String hint) {
        AuthDto.Identity actor = scopes.authenticated(auth);
        scopes.requireMatchingHints(actor, hint, target);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/check")
    public ResponseEntity<Void> checkPayload(Authentication auth,
                                 @Valid @RequestBody TenantRequest request,
                                 @RequestHeader(name = "X-Tenant-Id", required = false) String hint) {
        scopes.requireMatchingHints(scopes.authenticated(auth), hint, request.tenantId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('PERM_USER_READ')")
    public ResponseEntity<UserSummary> getUser(@PathVariable String userId, Authentication auth,
            @RequestParam(name = "tenantId", required = false) String tenantHint,
            @RequestHeader(name = "X-Tenant-Id", required = false) String header) {
        AuthDto.Identity actor = scopes.authenticated(auth);
        scopes.requireMatchingHints(actor, header, tenantHint);
        StaffUser user = scopes.findVisibleUser(actor, userId,
                tenantHint != null ? tenantHint : header);
        String owner = user.getTenant() == null ? null : user.getTenant().getId();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new UserSummary(user.getId(), user.getUsername(), owner, Set.copyOf(user.getRoles())));
    }
}
