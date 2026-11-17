package com.scentstation.operation.maintenance.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class ChecklistRedisUnavailableTest {
    @Test
    void redisFailureIsNotTreatedAsAnEmptyChecklist() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        MaintenanceChecklistCache cache = new MaintenanceChecklistCache(redis, new ObjectMapper(), Duration.ofSeconds(5));

        assertThrows(RedisConnectionFailureException.class, () -> cache.get(12L, 3L));
    }
}
