package com.scentstation.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.order.dto.response.OrderStatusMetadata;
import com.scentstation.order.entity.OrderStatus;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "order.cache.enabled", havingValue = "true")
public class OrderStatusMetadataCache {
    public static final String KEY_PREFIX = "scentstation:order:v1:status-metadata:tenant:";
    private static final TypeReference<List<OrderStatusMetadata>> VALUE_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public OrderStatusMetadataCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${order.cache.status-metadata-ttl:5m}") Duration ttl
    ) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("order.cache.status-metadata-ttl must be positive");
        }
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public static String key(String tenantId) {
        if (tenantId == null || !tenantId.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("positive tenantId required");
        }
        return KEY_PREFIX + tenantId;
    }

    public List<OrderStatusMetadata> getOrLoad(String tenantId) {
        String key = key(tenantId);
        String json = redis.opsForValue().get(key);
        if (json != null) {
            try {
                return objectMapper.readValue(json, VALUE_TYPE);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Unable to deserialize order status metadata", exception);
            }
        }
        List<OrderStatusMetadata> metadata = statusMetadata();
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(metadata), ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize order status metadata", exception);
        }
        return metadata;
    }

    public void evict(String tenantId) {
        redis.delete(key(tenantId));
    }

    public static List<OrderStatusMetadata> statusMetadata() {
        return List.of(
                new OrderStatusMetadata(OrderStatus.PENDING.name(), "Pending", false),
                new OrderStatusMetadata(OrderStatus.CONFIRMED.name(), "Confirmed", false),
                new OrderStatusMetadata(OrderStatus.DISPENSED.name(), "Dispensed", false),
                new OrderStatusMetadata(OrderStatus.PROCESSING.name(), "Processing", false),
                new OrderStatusMetadata(OrderStatus.SHIPPED.name(), "Shipped", false),
                new OrderStatusMetadata(OrderStatus.DELIVERED.name(), "Delivered", true),
                new OrderStatusMetadata(OrderStatus.CANCELLED.name(), "Cancelled", true),
                new OrderStatusMetadata(OrderStatus.EXPIRED.name(), "Expired", true),
                new OrderStatusMetadata(OrderStatus.FAILED.name(), "Failed", true)
        );
    }
}
