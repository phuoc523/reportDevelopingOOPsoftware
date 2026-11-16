package com.scentstation.identity.tenant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis-backed tenant display cache; failures are surfaced when enabled. */
@Component
@ConditionalOnProperty(name = "identity.cache.enabled", havingValue = "true")
public class RedisTenantViewCache implements TenantViewCache {
    public static final String KEY_PREFIX = "scentstation:identity:v1:tenant-views:tenant:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    public RedisTenantViewCache(StringRedisTemplate redis, ObjectMapper mapper,
                                @Value("${identity.cache.ttl:60s}") Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("identity.cache.ttl must be positive");
        }
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = ttl;
    }

    public static String key(Long tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        return KEY_PREFIX + tenantId;
    }

    @Override
    public TenantView get(Long tenantId) {
        String json = redis.opsForValue().get(key(tenantId));
        if (json == null) return null;
        try {
            return mapper.readValue(json, TenantView.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to deserialize tenant view cache", e);
        }
    }

    @Override
    public void put(Long tenantId, TenantView view) {
        if (view == null) return;
        if (!tenantId.equals(view.id())) {
            throw new IllegalArgumentException("Tenant view cache key mismatch");
        }
        try {
            redis.opsForValue().set(key(tenantId), mapper.writeValueAsString(view), ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize tenant view cache", e);
        }
    }

    @Override
    public void evict(Long tenantId) {
        redis.delete(key(tenantId));
    }
}
