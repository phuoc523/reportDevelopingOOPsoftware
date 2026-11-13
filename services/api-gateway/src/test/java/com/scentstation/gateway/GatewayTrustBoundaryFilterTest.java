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

    @Test void stripsSpoofedTenantAndInternalHeadersButPreservesBearer() {
        var request = MockServerHttpRequest.get("/api/catalog/items")
            .header("X-Tenant-Id", "other-tenant")
            .header("X-Verified-Role", "PLATFORM_SUPER_ADMIN")
            .header("X-Internal-Service-Key", "leaked")
            .header("X-Request-Id", "forged")
            .header("Authorization", "Bearer actual-token").build();
        var exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, e -> { forwarded.set(e); return Mono.empty(); }).block();
        assertNotNull(forwarded.get());
        var headers = forwarded.get().getRequest().getHeaders();
        assertNull(headers.getFirst("X-Tenant-Id"));
        assertNull(headers.getFirst("X-Verified-Role"));
        assertNull(headers.getFirst("X-Internal-Service-Key"));
        assertEquals("Bearer actual-token", headers.getFirst("Authorization"));
        assertNotEquals("forged", headers.getFirst("X-Request-Id"));
        assertEquals(headers.getFirst("X-Request-Id"),
            exchange.getResponse().getHeaders().getFirst("X-Request-Id"));
    }

    @Test void deniesInternalRoutes() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(
            "/api/v1/orders/internal/reconcile").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, e -> { forwarded.set(e); return Mono.empty(); }).block();
        assertNull(forwarded.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }
}
