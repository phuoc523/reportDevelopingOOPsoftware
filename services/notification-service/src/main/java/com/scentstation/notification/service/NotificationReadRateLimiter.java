package com.scentstation.notification.service;

import com.scentstation.notification.security.NotificationAuthorizationResponse;
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
public class NotificationReadRateLimiter {
    private static final String KEY_PREFIX = "scentstation:notification:v1:read-rate-limit:";
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

    public NotificationReadRateLimiter(
            StringRedisTemplate redis,
            @Value("${notification.rate-limit.enabled:true}") boolean enabled,
            @Value("${notification.rate-limit.requests:120}") long maxRequests,
            @Value("${notification.rate-limit.window:1m}") Duration window
    ) {
        if (maxRequests <= 0 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Notification rate-limit settings must be positive");
        }
        this.redis = redis;
        this.enabled = enabled;
        this.maxRequests = maxRequests;
        this.window = window;
    }

    public void check(NotificationAuthorizationResponse caller) {
        if (!enabled) {
            return;
        }
        String key = key(caller);
        final Long count;
        try {
            count = redis.execute(INCREMENT_WINDOW, List.of(key), Long.toString(window.toMillis()));
        } catch (DataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Notification rate limiter is unavailable", exception);
        }
        if (count == null) {
            throw new IllegalStateException("Redis returned no notification rate-limit counter");
        }
        if (count > maxRequests) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Notification read rate limit exceeded");
        }
    }

    static String key(NotificationAuthorizationResponse caller) {
        if (caller == null || caller.tenantId() == null || caller.tenantId().isBlank()) {
            throw new IllegalArgumentException("A verified notification tenant is required for rate limiting");
        }
        String subject;
        if (caller.isUser() && caller.userId() != null && caller.userId() > 0) {
            subject = "user:" + caller.userId();
        } else if ("SERVICE".equals(caller.callerType()) && caller.userId() == null) {
            subject = "service";
        } else {
            throw new IllegalArgumentException("A verified notification caller is required for rate limiting");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((caller.tenantId().trim() + ":" + subject).getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for notification rate-limit key hashing", exception);
        }
    }
}
