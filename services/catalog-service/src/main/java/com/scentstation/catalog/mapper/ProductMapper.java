package com.scentstation.catalog.mapper;

import com.scentstation.catalog.dto.request.CreateProductRequest;
import com.scentstation.catalog.dto.request.UpdateProductRequest;
import com.scentstation.catalog.dto.response.ProductResponse;
import com.scentstation.catalog.entity.Product;
import org.springframework.stereotype.Component;


@Component
public class ProductMapper {

    // Chuyển request thành Product bằng tenant đã được xác minh thay vì tenant do caller tự khai báo.
    public Product toEntity(CreateProductRequest request, Long tenantId) {
        return Product.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .description(request.getDescription())
                .fragranceNotes(request.getFragranceNotes())
                .imageUrl(request.getImageUrl())
                .defaultPrice(request.getDefaultPrice())
                .available(request.getAvailable())
                .build();
    }

    // Chuyển entity sản phẩm thành dữ liệu phản hồi gửi cho client.
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

    // Sao chép các giá trị được phép cập nhật từ request vào sản phẩm hiện có.
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
