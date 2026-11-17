package com.scentstation.operation.maintenance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.maintenance.dto.response.MaintenanceChecklistItemResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "operation.cache.enabled", havingValue = "true")
public class MaintenanceChecklistCache {
    public static final String KEY_PREFIX = "scentstation:operation:v1:checklist:tenant:";
    private static final TypeReference<List<CachedItem>> VALUE_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public MaintenanceChecklistCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${operation.cache.checklist-ttl:5s}") Duration ttl
    ) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("operation.cache.checklist-ttl must be positive");
        }
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public static String key(Long tenantId, Long ticketId) {
        if (tenantId == null || tenantId <= 0 || ticketId == null || ticketId <= 0) {
            throw new IllegalArgumentException("positive tenantId and ticketId required");
        }
        return KEY_PREFIX + tenantId + ":ticket:" + ticketId;
    }

    public List<MaintenanceChecklistItemResponse> get(Long tenantId, Long ticketId) {
        String json = redis.opsForValue().get(key(tenantId, ticketId));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, VALUE_TYPE).stream()
                    .map(CachedItem::toResponse)
                    .toList();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize maintenance checklist cache", exception);
        }
    }

    public void put(Long tenantId, Long ticketId, List<MaintenanceChecklistItemResponse> items) {
        if (items == null) {
            return;
        }
        List<CachedItem> values = items.stream().map(CachedItem::from).toList();
        try {
            redis.opsForValue().set(key(tenantId, ticketId), objectMapper.writeValueAsString(values), ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize maintenance checklist cache", exception);
        }
    }

    public void evict(Long tenantId, Long ticketId) {
        redis.delete(key(tenantId, ticketId));
    }

    private record CachedItem(
            Long id,
            Long tenantId,
            Long ticketId,
            String itemName,
            String description,
            boolean required,
            boolean completed,
            String resultNote,
            Long completedByActorId,
            LocalDateTime completedAt,
            LocalDateTime createdAt
    ) {
        private static CachedItem from(MaintenanceChecklistItemResponse value) {
            return new CachedItem(value.getId(), value.getTenantId(), value.getTicketId(),
                    value.getItemName(), value.getDescription(), value.isRequired(), value.isCompleted(),
                    value.getResultNote(), value.getCompletedByActorId(), value.getCompletedAt(),
                    value.getCreatedAt());
        }

        private MaintenanceChecklistItemResponse toResponse() {
            return MaintenanceChecklistItemResponse.builder()
                    .id(id)
                    .tenantId(tenantId)
                    .ticketId(ticketId)
                    .itemName(itemName)
                    .description(description)
                    .required(required)
                    .completed(completed)
                    .resultNote(resultNote)
                    .completedByActorId(completedByActorId)
                    .completedAt(completedAt)
                    .createdAt(createdAt)
                    .build();
        }
    }
}
