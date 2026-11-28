package com.scentstation.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import reactor.core.publisher.Mono;

@Component
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {
    private static final String KEY_PREFIX = "scentstation:gateway:v1:rate-limit:";

    private final RateLimitIdentityResolver identityResolver;
    private final GatewayRateLimitStore store;
    private final boolean enabled;
    private final long requestLimit;
    private final Duration window;

    public GatewayRateLimitFilter(
            RateLimitIdentityResolver identityResolver,
            GatewayRateLimitStore store,
            @Value("${gateway.rate-limit.enabled:true}") boolean enabled,
            @Value("${gateway.rate-limit.requests-per-minute:120}") long requestLimit,
            @Value("${gateway.rate-limit.window:1m}") Duration window
    ) {
        if (requestLimit <= 0 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Gateway rate-limit settings must be positive");
        }
        this.identityResolver = identityResolver;
        this.store = store;
        this.enabled = enabled;
        this.requestLimit = requestLimit;
        this.window = window;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled || shouldBypass(exchange)) {
            return chain.filter(exchange);
        }
        return identityResolver.resolve(exchange)
                .flatMap(identity -> store.increment(redisKey(identity), window))
                .onErrorResume(DataAccessException.class, ignored -> unavailable(exchange))
                .onErrorResume(RateLimitIdentityUnavailableException.class, ignored -> unavailable(exchange))
                .flatMap(count -> {
                    if (count <= requestLimit) {
                        return chain.filter(exchange);
                    }
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    exchange.getResponse().getHeaders().set("Retry-After",
                            Long.toString(Math.max(1, window.toSeconds())));
                    return exchange.getResponse().setComplete();
                });
    }

    private boolean shouldBypass(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())
                || "/actuator/health".equals(path)
                || "/api/auth/health".equals(path);
    }

    private String redisKey(String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for rate-limit key hashing", exception);
        }
    }

    private Mono<Long> unavailable(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return exchange.getResponse().setComplete().then(Mono.empty());
    }
}
