package com.scentstation.order.security;

import com.scentstation.order.integration.identity.client.IdentityIntrospectionClient;
import com.scentstation.order.integration.identity.dto.IdentityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequestScope
public class OrderCurrentActorContext {
    private final HttpServletRequest request;
    private final IdentityIntrospectionClient identity;
    private IdentityPrincipal principal;

    public OrderCurrentActorContext(HttpServletRequest request, IdentityIntrospectionClient identity) {
        this.request = request;
        this.identity = identity;
    }

    public IdentityPrincipal principal() {
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
