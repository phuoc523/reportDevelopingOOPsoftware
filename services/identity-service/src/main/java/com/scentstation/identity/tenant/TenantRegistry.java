package com.scentstation.identity.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.identity.repository.TenantRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantRegistry {

    private final TenantRepository repository;
    private final ObjectMapper objectMapper;

    public TenantRegistry(TenantRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Tenant create(
            String tenantCode, String name, String brandName, String configurationJson) {
        validateConfiguration(configurationJson);

        if (repository.existsByTenantCode(tenantCode)) {
            throw new IllegalArgumentException("Tenant code already exists");
        }

        Tenant tenant = new Tenant(tenantCode, name, brandName);
        tenant.updateDetails(name, brandName, configurationJson);

        try {
            return repository.saveAndFlush(tenant);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException("Tenant code already exists", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<Tenant> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Tenant findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + id));
    }

    @Transactional
    public Tenant update(
            UUID id, String name, String brandName, String configurationJson) {
        validateConfiguration(configurationJson);
        Tenant tenant = findById(id);
        tenant.updateDetails(name, brandName, configurationJson);
        return repository.save(tenant);
    }

    @Transactional
    public Tenant setActive(UUID id, boolean active) {
        Tenant tenant = findById(id);
        tenant.setActive(active);
        return repository.save(tenant);
    }

    private void validateConfiguration(String configurationJson) {
        if (configurationJson == null || configurationJson.isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(configurationJson);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("configurationJson must be a JSON object");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("configurationJson must be valid JSON", exception);
        }
    }
}
