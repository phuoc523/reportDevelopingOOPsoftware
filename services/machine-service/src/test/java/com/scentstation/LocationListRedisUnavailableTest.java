package com.scentstation;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.service.LocationListCache;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class LocationListRedisUnavailableTest {
    @Test
    void redisFailureIsNotTreatedAsAnEmptyLocationList() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        LocationListCache cache = new LocationListCache(redis, new ObjectMapper(), Duration.ofSeconds(60));

        assertThrows(RedisConnectionFailureException.class, () -> cache.get("12"));
    }
}
