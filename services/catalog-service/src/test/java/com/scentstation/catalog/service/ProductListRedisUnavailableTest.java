package com.scentstation.catalog.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class ProductListRedisUnavailableTest {
    @Test
    void redisFailureIsNotTreatedAsAnEmptyProductList() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        ProductListCache cache = new ProductListCache(redis, new ObjectMapper(), java.time.Duration.ofSeconds(60));

        assertThrows(RedisConnectionFailureException.class, () -> cache.get(1L));
    }
}
