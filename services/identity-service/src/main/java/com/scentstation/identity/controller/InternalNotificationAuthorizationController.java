package com.scentstation.identity.controller;

import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.rbac.AccessPolicy;
import com.scentstation.identity.rbac.AccountPrincipal;
import com.scentstation.identity.rbac.Permission;
import com.scentstation.identity.rbac.RolePermissions;
import com.scentstation.identity.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationAuthorizationController {
    private final AccessPolicy access;
    private final UserRepository users;
    private final String serviceKey;

    public InternalNotificationAuthorizationController(
            AccessPolicy access,
            UserRepository users,
            @Value("${security.internal.service-key:}") String serviceKey
    ) {
        this.access = access;
        this.users = users;
        this.serviceKey = serviceKey;
    }

    @PostMapping("/authorize-user")
    public AuthorizationResponse authorizeUser(
            @RequestHeader(value = "X-Internal-Service-Key", required = false) String suppliedKey,
            @RequestBody AuthorizationRequest request
    ) {
        requireInternalKey(suppliedKey);
        AccountPrincipal actor = access.current();
        Permission permission = requirePermission(request.permission());
        if (!RolePermissions.allowed(actor.roles(), actor.grants(), permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }

        Long tenantId = resolveUserTenant(actor, request.tenantId());
        return new AuthorizationResponse(
            "USER",
            actor.id(),
            tenantId.toString(),
            validateRecipients(tenantId, request.recipientIds())
        );
    }

    @PostMapping("/authorize-service")
    public AuthorizationResponse authorizeService(
            @RequestHeader(value = "X-Internal-Service-Key", required = false) String suppliedKey,
            @RequestHeader(value = "X-Service-Principal-Id", required = false) String serviceId,
            @RequestHeader(value = "X-Service-Principal-Credential", required = false) String credential,
            @RequestBody AuthorizationRequest request
    ) {
        requireInternalKey(suppliedKey);
        requireInternalKey(credential);
        if (serviceId == null || serviceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Service identity required");
        }
        requirePermission(request.permission());
        Long tenantId = requireTenantId(request.tenantId());
        return new AuthorizationResponse(
            "SERVICE",
            null,
            tenantId.toString(),
            validateRecipients(tenantId, request.recipientIds())
        );
    }

    @PostMapping("/resolve-recipients")
    public AuthorizationResponse resolveRecipients(
            @RequestHeader(value = "X-Internal-Service-Key", required = false) String suppliedKey,
            @RequestHeader(value = "X-Service-Principal-Id", required = false) String serviceId,
            @RequestHeader(value = "X-Service-Principal-Credential", required = false) String credential,
            @RequestBody AuthorizationRequest request
    ) {
        requireInternalKey(suppliedKey);
        requireInternalKey(credential);
        if (serviceId == null || serviceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Service identity required");
        }
        Permission permission = requirePermission(request.permission());
        Long tenantId = requireTenantId(request.tenantId());
        List<String> recipientIds = users.findByTenantId(tenantId).stream()
            .filter(User::isActive)
            .filter(user -> !user.isLocked())
            .filter(user -> {
                AccountPrincipal principal = AccountPrincipal.from(user);
                return RolePermissions.allowed(principal.roles(), principal.grants(), permission);
            })
            .map(User::getId)
            .map(String::valueOf)
            .toList();
        return new AuthorizationResponse("SERVICE", null, tenantId.toString(), recipientIds);
    }

    private Long resolveUserTenant(AccountPrincipal actor, String requestedTenantId) {
        Long requested = requestedTenantId == null || requestedTenantId.isBlank()
            ? null : requireTenantId(requestedTenantId);
        if (actor.roles().contains(Role.PLATFORM_SUPER_ADMIN)) {
            if (requested == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId is required");
            }
            return requested;
        }
        if (actor.tenantId() == null || (requested != null && !requested.equals(actor.tenantId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant access denied");
        }
        return actor.tenantId();
    }

    private List<String> validateRecipients(Long tenantId, List<String> recipientIds) {
        if (recipientIds == null || recipientIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        try {
            for (String recipientId : recipientIds) {
                long id = Long.parseLong(recipientId.trim());
                if (id <= 0) throw new NumberFormatException();
                ids.add(id);
            }
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient IDs must be positive numbers");
        }

        Map<Long, User> recipients = users.findAllById(ids).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
        boolean invalid = ids.stream().map(recipients::get).anyMatch(user ->
            user == null || !user.isActive() || user.isLocked() || !tenantId.equals(user.getTenantId()));
        if (invalid) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient not found in tenant");
        }
        return ids.stream().map(String::valueOf).toList();
    }

    private Permission requirePermission(String value) {
        try {
            Permission permission = Permission.valueOf(value);
            if (permission != Permission.NOTIFICATION_READ
                    && permission != Permission.NOTIFICATION_CONFIGURE) {
                throw new IllegalArgumentException();
            }
            return permission;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid notification permission");
        }
    }

    private Long requireTenantId(String value) {
        try {
            long tenantId = Long.parseLong(value == null ? "" : value.trim());
            if (tenantId <= 0) throw new NumberFormatException();
            return tenantId;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId must be a positive number");
        }
    }

    private void requireInternalKey(String suppliedKey) {
        if (serviceKey == null || serviceKey.isBlank() || suppliedKey == null
                || !MessageDigest.isEqual(
                    serviceKey.getBytes(StandardCharsets.UTF_8),
                    suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Service authentication required");
        }
    }

    public record AuthorizationRequest(String tenantId, String permission, List<String> recipientIds) {
    }

    public record AuthorizationResponse(
        String callerType,
        Long userId,
        String tenantId,
        List<String> validatedRecipientIds
    ) {
    }
}
