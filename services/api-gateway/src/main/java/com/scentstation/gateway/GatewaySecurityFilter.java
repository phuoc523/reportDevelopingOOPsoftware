package com.scentstation.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.Locale;
import java.util.UUID;

/** Reject internal paths even before proxy routing, and never trust client-supplied internal headers. */
@Component
public class GatewaySecurityFilter implements GlobalFilter, Ordered {
    @Override public int getOrder() { return -100; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath().toLowerCase(Locale.ROOT);
        // Deny encoded path separators, backslashes and dot-segments to avoid bypassing /internal/ checks.
        // These encodings are not needed in ScentStation's API route prefixes.
        if (path.contains("%2f") || path.contains("%5c") || path.contains("%2e")
                || path.contains("%25") || path.contains("\\")
                || path.contains("/../") || path.contains("/./") || path.endsWith("/..")
                || path.endsWith("/.") || path.contains("/internal/") || path.endsWith("/internal")) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        String requestId = UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set("X-Request-Id", requestId);
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            headers.remove("X-Internal-Service-Key");
            headers.remove("X-Verified-User-Id");
            headers.remove("X-Verified-Tenant-Id");
            headers.remove("X-Verified-Roles");
            headers.remove("Forwarded");
            headers.remove("X-Forwarded-For");
            headers.remove("X-Forwarded-Host");
            headers.remove("X-Forwarded-Proto");
            headers.set("X-Request-Id", requestId);
        }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }
}
