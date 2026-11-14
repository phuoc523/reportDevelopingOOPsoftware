package com.scentstation.identity.controller;

import com.scentstation.identity.rbac.AccessPolicy;
import com.scentstation.identity.rbac.AccountPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Internal service-to-service JWT introspection. NOT a public Gateway route. */
@RestController
@RequestMapping("/internal/auth")
public class InternalIntrospectionController {
    private final AccessPolicy access;
    private final String serviceKey;

    public InternalIntrospectionController(AccessPolicy access,
        @Value("${security.internal.service-key:}") String serviceKey) {
        this.access = access;
        this.serviceKey = serviceKey;
    }

    @PostMapping("/introspect")
    public AccountPrincipal introspect(@RequestHeader(value = "X-Internal-Service-Key", required = false)
                                        String suppliedKey) {
        if (serviceKey == null || serviceKey.isBlank() || suppliedKey == null ||
            !MessageDigest.isEqual(serviceKey.getBytes(StandardCharsets.UTF_8),
                suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Service authentication required");
        }
        return access.current();
    }
}
