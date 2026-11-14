package com.scentstation.order.security;

import com.scentstation.order.entity.Order;
import com.scentstation.order.integration.identity.dto.IdentityPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OrderAuthorization {
    public static final String KIOSK_ORDER = "KIOSK_ORDER";
    public static final String ORDER_READ = "ORDER_READ";
    public static final String ORDER_REVIEW = "ORDER_REVIEW";

    public IdentityPrincipal require(IdentityPrincipal actor, String permission) {
        if (!actor.grants().contains(permission)) {
            throw forbidden("Permission required: " + permission);
        }
        tenant(actor);
        return actor;
    }

    public String tenant(IdentityPrincipal actor) {
        if (actor.tenantId() == null) {
            throw forbidden("A tenant-scoped actor is required");
        }
        return OrderIdentifiers.canonical(actor.tenantId(), "tenantId");
    }

    public void requireRead(IdentityPrincipal actor, Order order) {
        requireSameTenant(actor, order);
        if (actor.grants().contains(ORDER_READ)) {
            return;
        }
        if (actor.grants().contains(KIOSK_ORDER) && actor.id().equals(order.getCustomerId())) {
            return;
        }
        throw forbidden("Order read access is not allowed");
    }

    public void requireReview(IdentityPrincipal actor, Order order) {
        require(actor, ORDER_REVIEW);
        requireSameTenant(actor, order);
    }

    public void requireSameTenant(IdentityPrincipal actor, Order order) {
        long actorTenant = actor.tenantId() == null
                ? -1
                : actor.tenantId();
        if (actorTenant <= 0) {
            throw forbidden("A tenant-scoped actor is required");
        }
        if (actorTenant != OrderIdentifiers.stored(order.getTenantId(), "order tenantId")) {
            throw forbidden("Order belongs to another tenant");
        }
    }

    private ResponseStatusException forbidden(String reason) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
    }
}
