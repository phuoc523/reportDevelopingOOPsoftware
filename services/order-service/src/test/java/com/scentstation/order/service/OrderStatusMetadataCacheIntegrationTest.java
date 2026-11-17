package com.scentstation.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.order.dto.response.OrderStatusMetadata;
import java.time.Duration;
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
class OrderStatusMetadataCacheIntegrationTest {
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
    void cachesTenantScopedDisplayMetadataAndEvicts() {
        OrderStatusMetadataCache cache =
                new OrderStatusMetadataCache(redis, new ObjectMapper(), Duration.ofMinutes(5));

        assertEquals("PONG", redis.getConnectionFactory().getConnection().ping());
        List<OrderStatusMetadata> firstTenant = cache.getOrLoad("901");
        assertEquals(firstTenant, cache.getOrLoad("901"));
        assertEquals(firstTenant, cache.getOrLoad("902"));
        assertEquals(300, redis.getExpire(OrderStatusMetadataCache.key("901")));
        assertEquals("PENDING", firstTenant.getFirst().code());
        assertEquals(true, firstTenant.stream().filter(value -> value.code().equals("DELIVERED"))
                .findFirst().orElseThrow().terminal());

        cache.evict("901");
        assertEquals(firstTenant, cache.getOrLoad("901"));
    }

    @Test
    void statusMetadataTtlExpires() throws InterruptedException {
        OrderStatusMetadataCache cache =
                new OrderStatusMetadataCache(redis, new ObjectMapper(), Duration.ofMillis(100));
        cache.getOrLoad("903");
        Thread.sleep(250);
        assertEquals(null, redis.opsForValue().get(OrderStatusMetadataCache.key("903")));
    }
}
