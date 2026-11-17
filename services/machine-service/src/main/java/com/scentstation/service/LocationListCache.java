package com.scentstation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.dto.response.LocationResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "machine.cache.enabled", havingValue = "true")
public class LocationListCache {
    public static final String KEY_PREFIX = "scentstation:machine:v1:locations:tenant:";
    private static final TypeReference<List<CachedLocation>> VALUE_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public LocationListCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${machine.cache.location-list-ttl:60s}") Duration ttl
    ) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("machine.cache.location-list-ttl must be positive");
        }
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public static String key(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId required");
        }
        return KEY_PREFIX + tenantId.trim();
    }

    public List<LocationResponse> get(String tenantId) {
        String json = redis.opsForValue().get(key(tenantId));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, VALUE_TYPE).stream()
                    .map(CachedLocation::toResponse)
                    .toList();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize location list cache", exception);
        }
    }

    public void put(String tenantId, List<LocationResponse> locations) {
        if (locations == null) {
            return;
        }
        List<CachedLocation> values = locations.stream().map(CachedLocation::from).toList();
        try {
            redis.opsForValue().set(key(tenantId), objectMapper.writeValueAsString(values), ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize location list cache", exception);
        }
    }

    public void evict(String tenantId) {
        redis.delete(key(tenantId));
    }

    private record CachedLocation(
            String locationId,
            String tenantId,
            String name,
            String address,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        private static CachedLocation from(LocationResponse value) {
            return new CachedLocation(value.getLocationId(), value.getTenantId(), value.getName(),
                    value.getAddress(), value.isActive(), value.getCreatedAt(), value.getUpdatedAt());
        }

        private LocationResponse toResponse() {
            return new LocationResponse(locationId, tenantId, name, address, active, createdAt, updatedAt);
        }
    }
}
