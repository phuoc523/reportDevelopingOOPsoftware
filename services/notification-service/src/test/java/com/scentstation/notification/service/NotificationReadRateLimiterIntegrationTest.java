package com.scentstation.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scentstation.notification.security.NotificationAuthorizationResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class NotificationReadRateLimiterIntegrationTest {
    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    private static StringRedisTemplate redis;

    @BeforeAll
    static void connectToRedis() {
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @Test
    void limitsAuthorizedReadsByTenantAndUserWithTtl() {
        NotificationReadRateLimiter limiter =
                new NotificationReadRateLimiter(redis, true, 2, Duration.ofMinutes(1));
        NotificationAuthorizationResponse tenantOneUser = caller("USER", 42L, "7");
        NotificationAuthorizationResponse otherUser = caller("USER", 43L, "7");
        NotificationAuthorizationResponse sameUserOtherTenant = caller("USER", 42L, "8");

        assertEquals("PONG", redis.getConnectionFactory().getConnection().ping());
        limiter.check(tenantOneUser);
        limiter.check(tenantOneUser);
        limiter.check(otherUser);
        limiter.check(sameUserOtherTenant);

        ResponseStatusException limited =
                assertThrows(ResponseStatusException.class, () -> limiter.check(tenantOneUser));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.getStatusCode());
        long ttl = redis.getExpire(NotificationReadRateLimiter.key(tenantOneUser));
        assertTrue(ttl > 0 && ttl <= 60);
    }

    private static NotificationAuthorizationResponse caller(String type, Long userId, String tenantId) {
        return new NotificationAuthorizationResponse(type, userId, tenantId, List.of());
    }
}
