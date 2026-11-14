package com.scentstation.catalog.security;

import com.scentstation.catalog.integration.identity.client.IdentityIntrospectionClient;
import com.scentstation.catalog.integration.identity.dto.IdentityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequestScope
public class IdentityCurrentActorContext implements CurrentActorContext {
    private final HttpServletRequest request;
    private final IdentityIntrospectionClient identity;
    private IdentityPrincipal principal;

    public IdentityCurrentActorContext(HttpServletRequest request, IdentityIntrospectionClient identity) {
        this.request = request;
        this.identity = identity;
    }

    @Override
    public String getActorId() {
        return principal().id().toString();
    }

    @Override
    public Long getTenantId() {
        return principal().tenantId();
    }

    @Override
    public Set<String> getRoles() {
        return principal().roles();
    }

    @Override
    public Set<String> getPermissions() {
        return principal().grants();
    }

    private IdentityPrincipal principal() {
        if (principal == null) {
            String authorization = request.getHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")
                    || authorization.length() <= 7 || authorization.length() > 8192) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token required");
            }
            principal = identity.introspect(authorization.substring(7));
        }
        return principal;
    }
}
