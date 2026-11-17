package com.scentstation.catalog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.catalog.dto.response.ProductResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "catalog.cache.enabled", havingValue = "true")
public class ProductListCache {
    public static final String KEY_PREFIX = "scentstation:catalog:v1:product-list:tenant:";
    private static final TypeReference<List<ProductResponse>> RESPONSE_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public ProductListCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${catalog.cache.product-list-ttl:60s}") Duration ttl
    ) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("catalog.cache.product-list-ttl must be positive");
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

    public List<ProductResponse> get(Long tenantId) {
        String json = redis.opsForValue().get(key(tenantId));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, RESPONSE_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize product list cache", exception);
        }
    }

    public void put(Long tenantId, List<ProductResponse> products) {
        if (products == null) {
            return;
        }
        try {
            redis.opsForValue().set(key(tenantId), objectMapper.writeValueAsString(products), ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize product list cache", exception);
        }
    }

    public void evict(Long tenantId) {
        redis.delete(key(tenantId));
    }
}
