package com.scentstation.catalog.controller;

import com.scentstation.catalog.dto.request.CreateProductRequest;
import com.scentstation.catalog.dto.response.ProductResponse;
import com.scentstation.catalog.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.scentstation.catalog.dto.request.UpdateProductAvailabilityRequest;
import com.scentstation.catalog.dto.request.UpdateProductPriceRequest;
import com.scentstation.catalog.dto.request.ProductSearchCriteria;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // Tiếp nhận yêu cầu tạo sản phẩm mới và trả về sản phẩm đã được lưu.
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {
        ProductResponse response = productService.createProduct(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // Trả về danh sách tất cả sản phẩm trong catalog.
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getProducts() {
        return ResponseEntity.ok(productService.getProducts());
    }

    // Tìm và trả về một sản phẩm theo mã định danh.
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    // Cập nhật trạng thái có sẵn của một sản phẩm.
    @PatchMapping("/{id}/availability")
    public ResponseEntity<ProductResponse> changeAvailability(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductAvailabilityRequest request
    ) {
        return ResponseEntity.ok(productService.changeAvailability(id, request));
    }

    // Cập nhật giá mặc định của một sản phẩm.
    @PatchMapping("/{id}/price")
    public ResponseEntity<ProductResponse> updatePrice(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductPriceRequest request
    ) {
        return ResponseEntity.ok(productService.updatePrice(id, request));
    }

    // Tìm kiếm và lọc catalog theo tenant, keyword, availability và khoảng giá.
    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> searchProducts(
            @Valid @ModelAttribute ProductSearchCriteria criteria
    ) {
        return ResponseEntity.ok(
                productService.searchProducts(criteria)
        );
    }
}
