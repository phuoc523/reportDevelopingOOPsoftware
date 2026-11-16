package com.scentstation.identity.tenant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisTenantViewCacheTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final RedisTenantViewCache cache = new RedisTenantViewCache(redis, mapper, Duration.ofSeconds(60));

    private TenantView view(long id) {
        return new TenantView(id, "brand", "Brand", null, null, null, null, null, Tenant.Status.ACTIVE);
    }

    @Test void keyIsTenantScoped() {
        assertEquals("scentstation:identity:v1:tenant-views:tenant:5", RedisTenantViewCache.key(5L));
        assertNotEquals(RedisTenantViewCache.key(5L), RedisTenantViewCache.key(6L));
        assertThrows(IllegalArgumentException.class, () -> RedisTenantViewCache.key(null));
    }

    @Test void doesNotCacheMissingView() {
        cache.put(5L, null);
        verifyNoInteractions(redis);
    }

    @Test void storesJsonWithTtlAndReadsItBack() {
        when(redis.opsForValue()).thenReturn(values);
        TenantView original = view(5L);
        cache.put(5L, original);
        verify(values).set(eq(RedisTenantViewCache.key(5L)), contains("\"code\":\"brand\""),
            eq(Duration.ofSeconds(60)));
        when(values.get(RedisTenantViewCache.key(5L))).thenReturn(
            "{\"id\":5,\"code\":\"brand\",\"name\":\"Brand\",\"logoUrl\":null," +
            "\"description\":null,\"contactEmail\":null,\"contactPhone\":null," +
            "\"kioskContent\":null,\"status\":\"ACTIVE\"}");
        assertEquals(original, cache.get(5L));
    }

    @Test void mismatchedTenantNeverStored() {
        assertThrows(IllegalArgumentException.class, () -> cache.put(6L, view(5L)));
        verifyNoInteractions(redis);
    }

    @Test void evictsOnlyRequestedTenant() {
        cache.evict(5L);
        verify(redis).delete(RedisTenantViewCache.key(5L));
        verifyNoMoreInteractions(redis);
    }

    @Test void invalidTtlRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new RedisTenantViewCache(redis, mapper, Duration.ZERO));
    }
}
