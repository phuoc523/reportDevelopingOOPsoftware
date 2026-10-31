package com.scentstation.catalog.service;

import com.scentstation.catalog.dto.request.CreateProductRequest;
import com.scentstation.catalog.dto.response.ProductResponse;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.exception.ProductNotFoundException;
import com.scentstation.catalog.mapper.ProductMapper;
import com.scentstation.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.scentstation.catalog.dto.request.UpdateProductAvailabilityRequest;
import com.scentstation.catalog.dto.request.UpdateProductPriceRequest;
import com.scentstation.catalog.dto.request.ProductSearchCriteria;
import com.scentstation.catalog.specification.ProductSpecification;
import com.scentstation.catalog.exception.InvalidProductSearchCriteriaException;
import com.scentstation.catalog.security.CurrentActorContext;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final CurrentActorContext currentActorContext;

    // Tạo sản phẩm bằng tenant đã xác minh và từ chối body giả tenant khác.
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Long tenantId = currentActorContext.requireTenant(request.getTenantId());
        Product product = productMapper.toEntity(request, tenantId);

        Product savedProduct = productRepository.save(product);

        return productMapper.toResponse(savedProduct);
    }

    // Chỉ liệt kê sản phẩm thuộc tenant hiện tại.
    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts() {
        return productRepository.findAllByTenantId(
                        currentActorContext.getTenantId()
                )
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }

    // Chỉ trả sản phẩm thuộc tenant hiện tại để tránh đọc chéo tenant.
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = findProductForCurrentTenant(id);

        return productMapper.toResponse(product);
    }

    // Chỉ thay đổi availability của sản phẩm thuộc tenant hiện tại.
    @Transactional
    public ProductResponse changeAvailability(Long id, UpdateProductAvailabilityRequest request) {
        Product product = findProductForCurrentTenant(id);
        product.setAvailable(request.getAvailable());
        Product updatedProduct = productRepository.save(product);

        return productMapper.toResponse(updatedProduct);
    }

    // Chỉ thay đổi giá của sản phẩm thuộc tenant hiện tại.
    @Transactional 
    public ProductResponse updatePrice(Long id, UpdateProductPriceRequest request) {
        Product product = findProductForCurrentTenant(id);
        product.setDefaultPrice(request.getDefaultPrice());
        Product updatedProduct = productRepository.save(product);

        return productMapper.toResponse(updatedProduct);
    }

    // Tìm sản phẩm theo id và trusted tenant, đồng thời che resource tenant khác bằng 404.
    private Product findProductForCurrentTenant(Long id) {
        return productRepository.findByIdAndTenantId(
                        id,
                        currentActorContext.getTenantId()
                )
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    // Tìm sản phẩm bằng tenant đã xác minh và không cho query override tenant.
    @Transactional(readOnly = true)
    public List<ProductResponse> searchProducts(
            ProductSearchCriteria criteria
    ) {
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
