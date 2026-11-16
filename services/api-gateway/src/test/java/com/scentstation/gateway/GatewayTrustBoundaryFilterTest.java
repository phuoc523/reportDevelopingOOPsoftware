package com.scentstation.gateway;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

class GatewayTrustBoundaryFilterTest {
    private final GatewayTrustBoundaryFilter filter = new GatewayTrustBoundaryFilter();

    @Test
    void stripsSpoofedIdentityAndInternalHeadersButPreservesBearer() {
        var request = MockServerHttpRequest.get("/api/catalog/items")
            .header("X-Tenant-Id", "other-tenant")
            .header("X-Verified-User-Id", "999")
            .header("X-Verified-Tenant-Id", "999")
            .header("X-Verified-Roles", "PLATFORM_SUPER_ADMIN")
            .header("X-Internal-Service-Key", "leaked")
            .header("X-Authenticated-User", "forged")
            .header("X-Request-Id", "forged")
            .header("Authorization", "Bearer actual-token").build();
        var exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, e -> { forwarded.set(e); return Mono.empty(); }).block();

        assertNotNull(forwarded.get());
        var headers = forwarded.get().getRequest().getHeaders();
        assertNull(headers.getFirst("X-Tenant-Id"));
        assertNull(headers.getFirst("X-Verified-User-Id"));
        assertNull(headers.getFirst("X-Verified-Tenant-Id"));
        assertNull(headers.getFirst("X-Verified-Roles"));
        assertNull(headers.getFirst("X-Internal-Service-Key"));
        assertNull(headers.getFirst("X-Authenticated-User"));
        assertEquals("Bearer actual-token", headers.getFirst("Authorization"));
        assertNotEquals("forged", headers.getFirst("X-Request-Id"));
        assertEquals(headers.getFirst("X-Request-Id"),
            exchange.getResponse().getHeaders().getFirst("X-Request-Id"));
    }

    @Test
    void deniesRoutedInternalSegments() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(
            "/api/v1/orders/internal/reconcile").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, e -> { forwarded.set(e); return Mono.empty(); }).block();

        assertNull(forwarded.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }
}
