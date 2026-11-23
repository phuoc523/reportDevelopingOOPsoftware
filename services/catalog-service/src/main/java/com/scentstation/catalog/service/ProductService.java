package com.scentstation.catalog.service;

import com.scentstation.catalog.dto.request.CreateProductRequest;
import com.scentstation.catalog.dto.response.ProductResponse;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.exception.ProductNotFoundException;
import com.scentstation.catalog.mapper.ProductMapper;
import com.scentstation.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.scentstation.catalog.dto.request.UpdateProductAvailabilityRequest;
import com.scentstation.catalog.dto.request.UpdateProductPriceRequest;
import com.scentstation.catalog.dto.request.UpdateProductRequest;
import com.scentstation.catalog.dto.request.ProductSearchCriteria;
import com.scentstation.catalog.specification.ProductSpecification;
import com.scentstation.catalog.exception.InvalidProductSearchCriteriaException;
import com.scentstation.catalog.security.CurrentActorContext;
import com.scentstation.catalog.security.CatalogPermissions;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final CurrentActorContext currentActorContext;
    private ProductListCache productListCache;

    @Autowired(required = false)
    public void setProductListCache(ProductListCache productListCache) {
        this.productListCache = productListCache;
    }

    // Tạo sản phẩm bằng tenant đã xác minh và từ chối body giả tenant khác.
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        currentActorContext.requirePermission(CatalogPermissions.MANAGE);
        Long tenantId = currentActorContext.requireTenant(request.getTenantId());
        Product product = productMapper.toEntity(request, tenantId);

        Product savedProduct = productRepository.save(product);
        evictProductListAfterCommit(tenantId);

        return productMapper.toResponse(savedProduct);
    }

    // Chỉ liệt kê sản phẩm thuộc tenant hiện tại, hoặc tenant đích tường minh của platform.
    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts(Long requestedTenantId) {
        currentActorContext.requirePermission(CatalogPermissions.READ);
        Long tenantId = currentActorContext.requireTenant(requestedTenantId);
        List<ProductResponse> cached = productListCache == null ? null : productListCache.get(tenantId);
        if (cached != null) {
            return cached;
        }
        List<ProductResponse> products = productRepository.findAllByTenantId(tenantId)
                .stream()
                .map(productMapper::toResponse)
                .toList();
        if (productListCache != null) {
            productListCache.put(tenantId, products);
        }
        return products;
    }

    // Chỉ trả sản phẩm thuộc tenant hiện tại để tránh đọc chéo tenant.
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        currentActorContext.requirePermission(CatalogPermissions.READ);
        Product product = findProductForCurrentTenant(id);

        return productMapper.toResponse(product);
    }

    // Chỉ thay đổi availability của sản phẩm thuộc tenant hiện tại.
    @Transactional
    public ProductResponse changeAvailability(Long id, UpdateProductAvailabilityRequest request) {
        currentActorContext.requirePermission(CatalogPermissions.MANAGE);
        Product product = findProductForCurrentTenant(id);
        product.setAvailable(request.getAvailable());
        Product updatedProduct = productRepository.save(product);
        evictProductListAfterCommit(product.getTenantId());

        return productMapper.toResponse(updatedProduct);
    }

    // Chỉ thay đổi giá của sản phẩm thuộc tenant hiện tại.
    @Transactional 
    public ProductResponse updatePrice(Long id, UpdateProductPriceRequest request) {
        currentActorContext.requirePermission(CatalogPermissions.MANAGE);
        Product product = findProductForCurrentTenant(id);
        product.setDefaultPrice(request.getDefaultPrice());
        Product updatedProduct = productRepository.save(product);
        evictProductListAfterCommit(product.getTenantId());

        return productMapper.toResponse(updatedProduct);
    }

    // Cập nhật toàn bộ dữ liệu catalog nhưng luôn khóa phạm vi theo tenant từ phiên đăng nhập.
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        currentActorContext.requirePermission(CatalogPermissions.MANAGE);
        Product product = findProductForCurrentTenant(id);
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setFragranceNotes(request.getFragranceNotes());
        product.setImageUrl(request.getImageUrl());
        product.setDefaultPrice(request.getDefaultPrice());
        product.setBottlePrice(request.getBottlePrice());
        product.setBottleVolumeMl(request.getBottleVolumeMl());
        product.setBottleStock(request.getBottleStock());
        product.setAvailable(request.getAvailable());
        Product updated = productRepository.save(product);
        evictProductListAfterCommit(product.getTenantId());
        return productMapper.toResponse(updated);
    }

    // Tìm sản phẩm theo id và trusted tenant, đồng thời che resource tenant khác bằng 404.
    private Product findProductForCurrentTenant(Long id) {
        return productRepository.findByIdAndTenantId(
                        id,
                        currentActorContext.requireTenant(null)
                )
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private void evictProductListAfterCommit(Long tenantId) {
        if (productListCache == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    productListCache.evict(tenantId);
                }
            });
        } else {
            productListCache.evict(tenantId);
        }
    }

    // Tìm sản phẩm bằng tenant đã xác minh và không cho query override tenant.
    @Transactional(readOnly = true)
    public List<ProductResponse> searchProducts(
            ProductSearchCriteria criteria
    ) {
        currentActorContext.requirePermission(CatalogPermissions.READ);
        Long tenantId = currentActorContext.requireTenant(criteria.getTenantId());
        criteria.setTenantId(tenantId);
        validatePriceRange(criteria);

        return productRepository
                .findAll(
                        ProductSpecification.from(criteria)
                )
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }

    // Kiểm tra khoảng giá tìm kiếm không bị đảo ngược hoặc chứa giá âm.
    private void validatePriceRange(
            ProductSearchCriteria criteria
    ) {
        if (criteria.getMinPrice() != null
                && criteria.getMinPrice()
                .signum() < 0) {

            throw new InvalidProductSearchCriteriaException(
                    "Minimum price must not be negative"
            );
        }

        if (criteria.getMaxPrice() != null
                && criteria.getMaxPrice()
                .signum() < 0) {

            throw new InvalidProductSearchCriteriaException(
                    "Maximum price must not be negative"
            );
        }

        if (criteria.getMinPrice() != null
                && criteria.getMaxPrice() != null
                && criteria.getMinPrice()
                .compareTo(
                        criteria.getMaxPrice()
                ) > 0) {

            throw new InvalidProductSearchCriteriaException(
                    "Minimum price must not exceed maximum price"
            );
        }
    }
}
