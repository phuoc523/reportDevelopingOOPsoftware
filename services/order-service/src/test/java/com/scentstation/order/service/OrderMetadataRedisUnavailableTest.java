package com.scentstation.order.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class OrderMetadataRedisUnavailableTest {
    @Test
    void readDoesNotFallbackToLocalMetadataWhenRedisIsUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        OrderStatusMetadataCache cache = new OrderStatusMetadataCache(redis, new ObjectMapper(), Duration.ofMinutes(5));

        assertThrows(RedisConnectionFailureException.class, () -> cache.getOrLoad("12"));
    }
}
