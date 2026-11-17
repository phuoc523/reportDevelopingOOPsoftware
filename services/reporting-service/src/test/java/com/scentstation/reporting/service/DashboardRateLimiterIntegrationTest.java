package com.scentstation.reporting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scentstation.reporting.dto.integration.IdentityActor;
import java.time.Duration;
import java.util.Set;
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
class DashboardRateLimiterIntegrationTest {
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
    void rateLimitsByTenantAndUserAndExpiresTheWindow() {
        DashboardRequestRateLimiter limiter = new DashboardRequestRateLimiter(
                redis, true, 2, Duration.ofSeconds(60));
        IdentityActor tenantOne = actor(8L, 12L);
        IdentityActor otherUser = actor(9L, 12L);
        IdentityActor tenantTwo = actor(8L, 13L);

        assertEquals("PONG", redis.getConnectionFactory().getConnection().ping());
        limiter.check(tenantOne);
        limiter.check(tenantOne);
        limiter.check(otherUser);
        limiter.check(tenantTwo);

        ResponseStatusException limited = assertThrows(
                ResponseStatusException.class, () -> limiter.check(tenantOne));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.getStatusCode());
        long ttl = redis.getExpire(DashboardRequestRateLimiter.key(tenantOne));
        assertTrue(ttl > 0 && ttl <= 60);
    }

    @Test
    void disabledRateLimitDoesNotUseRedis() {
        StringRedisTemplate unavailable = org.mockito.Mockito.mock(StringRedisTemplate.class);
        DashboardRequestRateLimiter limiter =
                new DashboardRequestRateLimiter(unavailable, false, 1, Duration.ofMinutes(1));

        limiter.check(actor(9L, 12L));

        org.mockito.Mockito.verifyNoInteractions(unavailable);
    }

    private IdentityActor actor(Long id, Long tenantId) {
        return new IdentityActor(id, "user-" + id, tenantId, Set.of(), Set.of(), Set.of(), Set.of());
    }
}
