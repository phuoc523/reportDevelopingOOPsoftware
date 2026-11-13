package com.scentstation.gateway;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Removes untrusted caller-supplied identity metadata before proxying. */
@Component
public class GatewayTrustBoundaryFilter implements GlobalFilter, Ordered {
    private static final Set<String> RESERVED = Set.of(
        "x-tenant-id", "x-user-id", "x-role", "x-roles", "x-permissions",
        "x-internal-service-key", "x-request-id", "x-correlation-id",
        "x-brand-id", "x-location-id", "x-machine-id", "x-device-id",
        "x-scope", "x-scopes");

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        // Never proxy internal APIs through the public Gateway.
        for (String segment : path.split("/")) {
            if ("internal".equalsIgnoreCase(segment)) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
        }
        String requestId = UUID.randomUUID().toString();
        var sanitizedRequest = exchange.getRequest().mutate().headers(headers -> {
            for (String name : Set.copyOf(headers.keySet())) {
                String lower = name.toLowerCase(Locale.ROOT);
                if (RESERVED.contains(lower) || lower.startsWith("x-verified-")
                    || lower.startsWith("x-internal-") || lower.startsWith("x-authenticated-")) {
                    headers.remove(name);
                }
            }
            headers.set("X-Request-Id", requestId);
        }).build();
        exchange.getResponse().getHeaders().set("X-Request-Id", requestId);
        return chain.filter(exchange.mutate().request(sanitizedRequest).build());
    }
}
