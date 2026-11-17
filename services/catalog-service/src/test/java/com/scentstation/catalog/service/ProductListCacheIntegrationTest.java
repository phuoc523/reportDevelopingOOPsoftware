package com.scentstation.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.catalog.dto.response.ProductResponse;
import java.math.BigDecimal;
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
class ProductListCacheIntegrationTest {
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
    void productListsAreTenantIsolatedCachedAndEvicted() {
        ProductListCache cache = new ProductListCache(redis, new ObjectMapper().findAndRegisterModules(),
                Duration.ofSeconds(60));
        ProductResponse tenantOneProduct = product(11L, 81L);
        ProductResponse tenantTwoProduct = product(22L, 82L);

        assertEquals("PONG", redis.getConnectionFactory().getConnection().ping());
        assertNull(cache.get(81L));
        cache.put(81L, List.of(tenantOneProduct));
        cache.put(82L, List.of(tenantTwoProduct));

        assertEquals(List.of(tenantOneProduct), cache.get(81L));
        assertEquals(List.of(tenantTwoProduct), cache.get(82L));
        assertEquals(60, redis.getExpire(ProductListCache.key(81L)));

        cache.evict(81L);
        assertNull(cache.get(81L));
        assertEquals(List.of(tenantTwoProduct), cache.get(82L));
    }

    @Test
    void productListTtlExpires() throws InterruptedException {
        ProductListCache cache = new ProductListCache(redis, new ObjectMapper().findAndRegisterModules(),
                Duration.ofMillis(100));
        cache.put(83L, List.of(product(33L, 83L)));
        Thread.sleep(250);
        assertNull(cache.get(83L));
    }

    private ProductResponse product(Long id, Long tenantId) {
        return ProductResponse.builder().id(id).tenantId(tenantId).name("Scent")
                .defaultPrice(new BigDecimal("19.99")).available(true)
                .createdAt(LocalDateTime.now()).build();
    }
}
