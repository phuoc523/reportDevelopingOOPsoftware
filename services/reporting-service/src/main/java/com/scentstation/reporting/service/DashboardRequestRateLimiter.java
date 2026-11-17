package com.scentstation.reporting.service;

import com.scentstation.reporting.dto.integration.IdentityActor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DashboardRequestRateLimiter {
    private static final String KEY_PREFIX = "scentstation:reporting:v1:dashboard-rate-limit:";
    private static final DefaultRedisScript<Long> INCREMENT_WINDOW = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return count;",
            Long.class
    );

    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final long maxRequests;
    private final Duration window;

    public DashboardRequestRateLimiter(
            StringRedisTemplate redis,
            @Value("${reporting.rate-limit.dashboard.enabled:true}") boolean enabled,
            @Value("${reporting.rate-limit.dashboard.max-requests:120}") long maxRequests,
            @Value("${reporting.rate-limit.dashboard.window:1m}") Duration window
    ) {
        if (maxRequests <= 0 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Reporting dashboard rate-limit settings must be positive");
        }
        this.redis = redis;
        this.enabled = enabled;
        this.maxRequests = maxRequests;
        this.window = window;
    }

    public void check(IdentityActor actor) {
        if (!enabled) {
            return;
        }
        String key = key(actor);
        final Long count;
        try {
            count = redis.execute(INCREMENT_WINDOW, List.of(key), Long.toString(window.toMillis()));
        } catch (DataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Dashboard rate limiter is unavailable", exception);
        }
        if (count == null) {
            throw new IllegalStateException("Redis returned no dashboard rate-limit counter");
        }
        if (count > maxRequests) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Dashboard request rate limit exceeded");
        }
    }

    static String key(IdentityActor actor) {
        if (actor == null || actor.id() == null || actor.id() <= 0
                || (actor.tenantId() != null && actor.tenantId() <= 0)) {
            throw new IllegalArgumentException("A verified user identity is required for dashboard rate limiting");
        }
        String tenant = actor.tenantId() == null ? "platform" : actor.tenantId().toString();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((tenant + ":" + actor.id()).getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for dashboard rate-limit key hashing", exception);
        }
    }
}
