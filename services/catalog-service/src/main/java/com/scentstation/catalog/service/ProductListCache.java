package com.scentstation.catalog.service;

import com.scentstation.catalog.dto.response.ProductResponse;
import com.scentstation.catalog.mapper.ProductMapper;
import com.scentstation.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/** Caches only tenant-scoped catalog lists; authorization remains in ProductService. */
@Service
@RequiredArgsConstructor
public class ProductListCache {
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Cacheable(cacheNames = "catalog-product-list", key = "#p0", sync = true)
    public List<ProductResponse> getForTenant(Long tenantId) {
        return productRepository.findAllByTenantId(tenantId).stream()
            .map(productMapper::toResponse)
            .toList();
    }

    @CacheEvict(cacheNames = "catalog-product-list", allEntries = true)
    public void evictAll() {
        // Cache eviction is intentionally scoped to this dedicated cache.
    }
}
