package com.scentstation.reporting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

import com.scentstation.reporting.dto.integration.IdentityActor;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DashboardRateLimiterRedisUnavailableTest {
    @Test
    void redisFailureIsReturnedAsServiceUnavailable() {
        StringRedisTemplate redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        doThrow(new RedisConnectionFailureException("Redis unavailable"))
                .when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        DashboardRequestRateLimiter limiter =
                new DashboardRequestRateLimiter(redis, true, 120, Duration.ofMinutes(1));
        IdentityActor actor = new IdentityActor(1L, "user", 12L, Set.of(), Set.of(), Set.of(), Set.of());

        ResponseStatusException failure =
                assertThrows(ResponseStatusException.class, () -> limiter.check(actor));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatusCode());
    }
}
