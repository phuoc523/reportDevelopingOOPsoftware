package com.scentstation.identity.tenant;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Dedicated, opt-in live tests. Never point REDIS_TEST_PORT at shared Redis. */
@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
class RedisTenantViewLiveTest {
    private static final long A = 900000001L;
    private static final long B = 900000002L;
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate template;
    private static RedisTenantViewCache cache;

    @BeforeAll static void start() {
        String host = System.getenv().getOrDefault("REDIS_TEST_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_PORT", "16379"));
        factory = new LettuceConnectionFactory(host, port);
        factory.afterPropertiesSet();
        factory.start();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        cache = new RedisTenantViewCache(template, new ObjectMapper(), Duration.ofSeconds(2));
        cache.evict(A);
        cache.evict(B);
    }

    @AfterAll static void stop() {
        if (template != null) {
            cache.evict(A);
            cache.evict(B);
        }
        if (factory != null) factory.destroy();
    }

    private TenantView view(long id, String name) {
        return new TenantView(id, "code", name, null, null, null, null, null, Tenant.Status.ACTIVE);
    }

    @Test void missingKeyIsCacheMiss() {
        cache.evict(A);
        assertNull(cache.get(A));
    }

    @Test void storedViewRoundTripsThroughRealRedis() {
        TenantView original = view(A, "A");
        cache.put(A, original);
        assertEquals(original, cache.get(A));
    }

    @Test void keysSeparateTenants() {
        cache.put(A, view(A, "A"));
        cache.put(B, view(B, "B"));
        assertEquals("A", cache.get(A).name());
        assertEquals("B", cache.get(B).name());
    }

    @Test void positiveTtlIsApplied() {
        cache.put(A, view(A, "A"));
        Long ttl = template.getExpire(RedisTenantViewCache.key(A));
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 2);
    }

    @Test void ttlExpiryRemovesView() throws Exception {
        cache.put(A, view(A, "A"));
        Thread.sleep(2300);
        assertNull(cache.get(A));
    }

    @Test void evictionRemovesOnlyTargetTenant() {
        cache.put(A, view(A, "A"));
        cache.put(B, view(B, "B"));
        cache.evict(A);
        assertNull(cache.get(A));
        assertEquals("B", cache.get(B).name());
    }

    @Test void nullViewIsNotWritten() {
        cache.evict(A);
        cache.put(A, null);
        assertNull(cache.get(A));
    }
}
