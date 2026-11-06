package com.scentstation.identity.tenant;

import com.scentstation.identity.auth.AuthDto;
import com.scentstation.identity.auth.SessionService;
import com.scentstation.identity.rbac.RbacException;
import com.scentstation.identity.rbac.RbacPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.stream.Collectors;

/** Internal-only endpoint. Network policy MUST block it at public gateway. */
@RestController
@RequestMapping("/api/v1/internal/tenant")
public class InternalTenantIntrospectionController {
    private final SessionService sessions;
    private final String internalKey;

    public InternalTenantIntrospectionController(SessionService sessions,
            @Value("${TENANT_INTROSPECTION_KEY:}") String internalKey) {
        this.sessions = sessions;
        this.internalKey = internalKey;
    }

    public record TokenRequest(@NotBlank String accessToken) {}
    public record VerifiedIdentity(String userId, String username, String tenantId,
                                   Set<String> roles, Set<String> permissions) {}

    @PostMapping("/introspect")
    public ResponseEntity<VerifiedIdentity> introspect(
            @RequestHeader(name = "X-Internal-Service-Key", required = false) String supplied,
            @Valid @RequestBody TokenRequest request) {
        // Fail closed when integration has not been configured.
        if (internalKey == null || internalKey.length() < 32) {
            throw new RbacException(HttpStatus.SERVICE_UNAVAILABLE, "INTROSPECTION_NOT_CONFIGURED");
        }
        byte[] expected = internalKey.getBytes(StandardCharsets.UTF_8);
        byte[] received = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, received)) {
            throw new RbacException(HttpStatus.UNAUTHORIZED, "SERVICE_UNAUTHENTICATED");
        }
        AuthDto.Identity actor = sessions.resolve(request.accessToken());
        if (actor == null) {
            throw new RbacException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        }
        Set<String> permissions = RbacPolicy.permissionsFor(actor.roles()).stream()
                .map(Enum::name).collect(Collectors.toUnmodifiableSet());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new VerifiedIdentity(actor.userId(), actor.username(), actor.tenantId(),
                        actor.roles(), permissions));
    }
}
