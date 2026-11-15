package com.scentstation.identity.tenant;

import com.scentstation.identity.repository.TenantRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantRegistry {

    private final TenantRepository repository;

    public TenantRegistry(TenantRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Tenant create(String tenantCode) {
        String normalizedCode = requireCode(tenantCode);
        if (repository.existsByTenantCode(normalizedCode)) {
            throw new IllegalArgumentException("Tenant code already exists");
        }

        try {
            return repository.saveAndFlush(new Tenant(normalizedCode));
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException("Tenant code already exists", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<Tenant> findAll() {
        return repository.findAllByOrderByTenantCodeAsc();
    }

    @Transactional(readOnly = true)
    public Tenant findById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + id));
    }

    @Transactional
    public Tenant updateCode(String id, String code) {
        Tenant tenant = findById(id);
        String normalizedCode = requireCode(code);
        if (!tenant.getTenantCode().equals(normalizedCode)
                && repository.existsByTenantCode(normalizedCode)) {
            throw new IllegalArgumentException("Tenant code already exists");
        }
        tenant.setTenantCode(normalizedCode);
        return repository.save(tenant);
    }

    @Transactional
    public Tenant setActive(String id, boolean active) {
        Tenant tenant = findById(id);
        tenant.setActive(active);
        return repository.save(tenant);
    }

    private String requireCode(String value) {
        if (value == null || value.isBlank() || value.length() > 80) {
            throw new IllegalArgumentException("Tenant code is required (max 80 characters)");
        }
        return value.trim();
    }
}
