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

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = productMapper.toEntity(request);

        Product savedProduct = productRepository.save(product);

        return productMapper.toResponse(savedProduct);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts() {
        return productRepository.findAll()
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = findProductById(id);

        return productMapper.toResponse(product);
    }

    @Transactional
    public ProductResponse changeAvailability(Long id, UpdateProductAvailabilityRequest request) {
        Product product = findProductById(id);
        product.setAvailable(request.getAvailable());
        Product updatedProduct = productRepository.save(product);

        return productMapper.toResponse(updatedProduct);
    }

    @Transactional 
    public ProductResponse updatePrice(Long id, UpdateProductPriceRequest request) {
        Product product = findProductById(id);
        product.setDefaultPrice(request.getDefaultPrice());
        Product updatedProduct = productRepository.save(product);

        return productMapper.toResponse(updatedProduct);
    }

    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }
}