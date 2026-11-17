package com.scentstation.operation.maintenance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.maintenance.dto.response.MaintenanceChecklistItemResponse;
import java.time.Duration;
import java.time.LocalDateTime;
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
class MaintenanceChecklistCacheIntegrationTest {
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
    void checklistViewsAreScopedToTenantAndTicketAndEvicted() {
        MaintenanceChecklistCache cache = new MaintenanceChecklistCache(
                redis, new ObjectMapper().findAndRegisterModules(), Duration.ofSeconds(5));
        MaintenanceChecklistItemResponse item = item(11L, 101L, 201L, "Inspect nozzle");
        MaintenanceChecklistItemResponse other = item(12L, 102L, 201L, "Check filter");

        assertEquals("PONG", redis.getConnectionFactory().getConnection().ping());
        assertNull(cache.get(101L, 201L));
        cache.put(101L, 201L, List.of(item));
        cache.put(102L, 201L, List.of(other));

        assertEquals(List.of(item), cache.get(101L, 201L));
        assertEquals(List.of(other), cache.get(102L, 201L));
        assertEquals(5, redis.getExpire(MaintenanceChecklistCache.key(101L, 201L)));

        cache.evict(101L, 201L);
        assertNull(cache.get(101L, 201L));
        assertEquals(List.of(other), cache.get(102L, 201L));
    }

    @Test
    void checklistTtlExpires() throws InterruptedException {
        MaintenanceChecklistCache cache = new MaintenanceChecklistCache(
                redis, new ObjectMapper().findAndRegisterModules(), Duration.ofMillis(100));
        cache.put(103L, 202L, List.of(item(13L, 103L, 202L, "stale")));
        Thread.sleep(250);
        assertNull(cache.get(103L, 202L));
    }

    private MaintenanceChecklistItemResponse item(Long id, Long tenantId, Long ticketId, String name) {
        return MaintenanceChecklistItemResponse.builder()
                .id(id).tenantId(tenantId).ticketId(ticketId).itemName(name)
                .required(true).completed(false).createdAt(LocalDateTime.now()).build();
    }
}
