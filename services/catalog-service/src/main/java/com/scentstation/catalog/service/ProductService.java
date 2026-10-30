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

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    // Tạo và lưu một sản phẩm mới từ dữ liệu đầu vào.
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = productMapper.toEntity(request);

        Product savedProduct = productRepository.save(product);

        return productMapper.toResponse(savedProduct);
    }

    // Lấy toàn bộ sản phẩm và chuyển chúng sang dữ liệu phản hồi.
    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts() {
        return productRepository.findAll()
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }

    // Lấy thông tin một sản phẩm theo mã định danh.
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = findProductById(id);

        return productMapper.toResponse(product);
    }

    // Thay đổi trạng thái có sẵn của sản phẩm và lưu kết quả.
    @Transactional
    public ProductResponse changeAvailability(Long id, UpdateProductAvailabilityRequest request) {
        Product product = findProductById(id);
        product.setAvailable(request.getAvailable());
        Product updatedProduct = productRepository.save(product);

        return productMapper.toResponse(updatedProduct);
    }

    // Thay đổi giá mặc định của sản phẩm và lưu kết quả.
    @Transactional 
    public ProductResponse updatePrice(Long id, UpdateProductPriceRequest request) {
        Product product = findProductById(id);
        product.setDefaultPrice(request.getDefaultPrice());
        Product updatedProduct = productRepository.save(product);

        return productMapper.toResponse(updatedProduct);
    }

    // Tìm entity sản phẩm hoặc báo lỗi nếu mã định danh không tồn tại.
    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    // Tìm và lọc sản phẩm theo các điều kiện catalog được cung cấp.
    @Transactional(readOnly = true)
    public List<ProductResponse> searchProducts(
            ProductSearchCriteria criteria
    ) {
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
