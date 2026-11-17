package com.scentstation.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

import com.scentstation.notification.security.NotificationAuthorizationResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class NotificationReadRateLimiterRedisUnavailableTest {
    @Test
    void redisFailureIsReturnedAsServiceUnavailable() {
        StringRedisTemplate redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        doThrow(new RedisConnectionFailureException("Redis unavailable"))
                .when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        NotificationReadRateLimiter limiter =
                new NotificationReadRateLimiter(redis, true, 120, Duration.ofMinutes(1));
        NotificationAuthorizationResponse caller =
                new NotificationAuthorizationResponse("USER", 42L, "7", List.of());

        ResponseStatusException failure =
                assertThrows(ResponseStatusException.class, () -> limiter.check(caller));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatusCode());
    }
}
