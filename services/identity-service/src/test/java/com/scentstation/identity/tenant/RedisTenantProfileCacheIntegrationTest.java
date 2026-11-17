package com.scentstation.identity.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RedisTenantProfileCacheIntegrationTest {
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
    void cachesProfilesByTenantWithTtlAndEvicts() {
        RedisTenantProfileCache cache = new RedisTenantProfileCache(redis, new ObjectMapper(), Duration.ofSeconds(60));
        TenantProfileSnapshot tenantOne = new TenantProfileSnapshot(
                71001L, "one", "Tenant One", null, "one", null, null, null);
        TenantProfileSnapshot tenantTwo = new TenantProfileSnapshot(
                71002L, "two", "Tenant Two", null, "two", null, null, null);

        assertEquals("PONG", redis.getConnectionFactory().getConnection().ping());
        assertNull(cache.get(tenantOne.id()));
        cache.put(tenantOne.id(), tenantOne);
        cache.put(tenantTwo.id(), tenantTwo);

        assertEquals(tenantOne, cache.get(tenantOne.id()));
        assertEquals(tenantTwo, cache.get(tenantTwo.id()));
        assertEquals(60, redis.getExpire(RedisTenantProfileCache.key(tenantOne.id())));

        cache.evict(tenantOne.id());
        assertNull(cache.get(tenantOne.id()));
        assertEquals(tenantTwo, cache.get(tenantTwo.id()));
    }

    @Test
    void profileTtlExpires() throws InterruptedException {
        RedisTenantProfileCache cache = new RedisTenantProfileCache(redis, new ObjectMapper(), Duration.ofMillis(100));
        TenantProfileSnapshot profile = new TenantProfileSnapshot(
                71003L, "three", "Tenant Three", null, null, null, null, null);

        cache.put(profile.id(), profile);
        Thread.sleep(250);

        assertNull(cache.get(profile.id()));
    }
}
