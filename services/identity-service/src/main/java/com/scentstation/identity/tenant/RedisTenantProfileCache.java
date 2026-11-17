package com.scentstation.identity.tenant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "identity.cache.enabled", havingValue = "true")
public class RedisTenantProfileCache implements TenantProfileCache {
    public static final String KEY_PREFIX = "scentstation:identity:v1:tenant-profile:tenant:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisTenantProfileCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${identity.cache.ttl:60s}") Duration ttl
    ) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("identity.cache.ttl must be positive");
        }
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public static String key(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("positive tenantId required");
        }
        return KEY_PREFIX + tenantId;
    }

    @Override
    public TenantProfileSnapshot get(Long tenantId) {
        String json = redis.opsForValue().get(key(tenantId));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, TenantProfileSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize tenant profile cache", exception);
        }
    }

    @Override
    public void put(Long tenantId, TenantProfileSnapshot profile) {
        if (profile == null) {
            return;
        }
        if (!tenantId.equals(profile.id())) {
            throw new IllegalArgumentException("Tenant profile key mismatch");
        }
        try {
            redis.opsForValue().set(key(tenantId), objectMapper.writeValueAsString(profile), ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize tenant profile cache", exception);
        }
    }

    @Override
    public void evict(Long tenantId) {
        redis.delete(key(tenantId));
    }
}
