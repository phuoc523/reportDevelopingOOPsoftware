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

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {
        ProductResponse response = productService.createProduct(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getProducts() {
        return ResponseEntity.ok(productService.getProducts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @PatchMapping("/{id}/availability")
    public ResponseEntity<ProductResponse> changeAvailability(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductAvailabilityRequest request
    ) {
        return ResponseEntity.ok(productService.changeAvailability(id, request));
    }

    @PatchMapping("/{id}/price")
    public ResponseEntity<ProductResponse> updatePrice(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductPriceRequest request
    ) {
        return ResponseEntity.ok(productService.updatePrice(id, request));
    }
}