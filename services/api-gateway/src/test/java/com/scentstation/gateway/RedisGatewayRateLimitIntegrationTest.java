package com.scentstation.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RedisGatewayRateLimitIntegrationTest {
    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static ReactiveStringRedisTemplate redis;

    @BeforeAll
    static void connectToRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new ReactiveStringRedisTemplate(connectionFactory);
    }

    @Test
    void incrementsAtomicallyAndExpiresTheRateWindow() throws InterruptedException {
        RedisGatewayRateLimitStore store = new RedisGatewayRateLimitStore(redis);
        String key = "scentstation:gateway:v1:rate-limit:integration-test";

        assertEquals("PONG", connectionFactory.getConnection().ping());
        assertEquals(1L, store.increment(key, Duration.ofMillis(150)).block());
        assertEquals(2L, store.increment(key, Duration.ofMillis(150)).block());
        Thread.sleep(300);
        assertEquals(1L, store.increment(key, Duration.ofMillis(150)).block());
    }
}
