package com.scentstation.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class GatewayRateLimitFilterTest {
    @Test
    void returns429WhenTrustedIdentityExceedsTheWindowLimit() {
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(
                exchange -> Mono.just("identity:12:8"),
                (key, window) -> Mono.just(3L),
                true,
                2,
                Duration.ofMinutes(1)
        );
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products").build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("60", exchange.getResponse().getHeaders().getFirst("Retry-After"));
        assertTrue(!forwarded.get());
    }

    @Test
    void redisFailureReturns503AndNeverForwardsTheRequest() {
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(
                exchange -> Mono.just("identity:12:8"),
                (key, window) -> Mono.error(new RedisConnectionFailureException("Redis unavailable")),
                true,
                2,
                Duration.ofMinutes(1)
        );
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders").build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertTrue(!forwarded.get());
    }

    @Test
    void errorsFromForwardedRequestsAreHandledByTheGatewayRatherThanTheLimiter() {
        var filter = new GatewayRateLimitFilter(
                exchange -> Mono.just("identity:12:8"),
                (key, window) -> Mono.just(1L),
                true, 2, Duration.ofMinutes(1));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders").build());
        assertThrows(RedisConnectionFailureException.class, () -> filter.filter(exchange,
                ignored -> Mono.error(new RedisConnectionFailureException("Downstream failure"))).block());
    }

    @Test
    void healthChecksAndCorsPreflightBypassRateLimiting() {
        RateLimitIdentityResolver resolver = exchange -> Mono.error(
                new AssertionError("bypassed requests must not resolve an identity"));
        GatewayRateLimitStore store = (key, window) -> Mono.error(
                new AssertionError("bypassed requests must not access Redis"));
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(
                resolver, store, true, 2, Duration.ofMinutes(1));
        for (MockServerHttpRequest request : new MockServerHttpRequest[] {
                MockServerHttpRequest.options("/api/products").build(),
                MockServerHttpRequest.get("/actuator/health").build(),
                MockServerHttpRequest.get("/api/auth/health").build()
        }) {
            var exchange = MockServerWebExchange.from(request);
            AtomicBoolean forwarded = new AtomicBoolean();
            filter.filter(exchange, ignored -> {
                forwarded.set(true);
                return Mono.empty();
            }).block();
            assertTrue(forwarded.get());
        }
    }

    @Test
    void disabledRateLimitPassesThroughWithoutRedis() {
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(
                exchange -> Mono.error(new AssertionError("disabled rate limiter must not resolve identity")),
                (key, window) -> Mono.error(new AssertionError("disabled rate limiter must not use Redis")),
                false,
                2,
                Duration.ofMinutes(1)
        );
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products").build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertTrue(forwarded.get());
    }
}
