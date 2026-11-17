package com.scentstation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.dto.response.LocationResponse;
import com.scentstation.service.LocationListCache;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class LocationListCacheIntegrationTest {
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
    void cachedLocationsAreTenantScopedAndEvictable() {
        LocationListCache cache = new LocationListCache(redis, new ObjectMapper().findAndRegisterModules(),
                Duration.ofSeconds(60));
        LocationResponse one = location("one", "91");
        LocationResponse two = location("two", "92");

        assertEquals("PONG", redis.getConnectionFactory().getConnection().ping());
        assertNull(cache.get("91"));
        cache.put("91", List.of(one));
        cache.put("92", List.of(two));

        assertEquals(List.of(one), cache.get("91"));
        assertEquals(List.of(two), cache.get("92"));
        assertEquals(60, redis.getExpire(LocationListCache.key("91")));

        cache.evict("91");
        assertNull(cache.get("91"));
        assertEquals(List.of(two), cache.get("92"));
    }

    @Test
    void locationTtlExpires() throws InterruptedException {
        LocationListCache cache = new LocationListCache(redis, new ObjectMapper().findAndRegisterModules(),
                Duration.ofMillis(100));
        cache.put("93", List.of(location("stale", "93")));
        Thread.sleep(250);
        assertNull(cache.get("93"));
    }

    private LocationResponse location(String id, String tenantId) {
        Instant now = Instant.now();
        return new LocationResponse(id, tenantId, "Location " + id, "Address", true, now, now);
    }
}
