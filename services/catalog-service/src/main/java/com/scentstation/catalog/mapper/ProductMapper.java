package com.scentstation.catalog.mapper;

import com.scentstation.catalog.dto.request.CreateProductRequest;
import com.scentstation.catalog.dto.request.UpdateProductRequest;
import com.scentstation.catalog.dto.response.ProductResponse;
import com.scentstation.catalog.entity.Product;
import org.springframework.stereotype.Component;


@Component
public class ProductMapper {

    public Product toEntity(CreateProductRequest request) {
        return Product.builder()
                .tenantId(request.getTenantId())
                .name(request.getName())
                .description(request.getDescription())
                .fragranceNotes(request.getFragranceNotes())
                .imageUrl(request.getImageUrl())
                .defaultPrice(request.getDefaultPrice())
                .available(request.getAvailable())
                .build();
    }

    public ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .tenantId(product.getTenantId())
                .name(product.getName())
                .description(product.getDescription())
                .fragranceNotes(product.getFragranceNotes())
                .imageUrl(product.getImageUrl())
                .defaultPrice(product.getDefaultPrice())
                .available(product.getAvailable())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public void updateEntity(
            UpdateProductRequest request,
            Product product
    ) {
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setFragranceNotes(request.getFragranceNotes());
        product.setImageUrl(request.getImageUrl());
        product.setDefaultPrice(request.getDefaultPrice());
        product.setAvailable(request.getAvailable());
    }
}