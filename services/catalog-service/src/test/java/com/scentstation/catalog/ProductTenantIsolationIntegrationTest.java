package com.scentstation.catalog;

import com.scentstation.catalog.dto.request.CreateProductRequest;
import com.scentstation.catalog.dto.request.UpdateProductAvailabilityRequest;
import com.scentstation.catalog.dto.request.UpdateProductPriceRequest;
import com.scentstation.catalog.exception.ProductNotFoundException;
import com.scentstation.catalog.repository.ProductRepository;
import com.scentstation.catalog.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductTenantIsolationIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void cleanDatabase() {
        productRepository.deleteAll();
    }

    @Test
    void tenantCannotReadOrUpdateAnotherTenantsProduct() {
        var product = productService.createProduct(CreateProductRequest.builder()
                .tenantId(1L)
                .name("Tenant one product")
                .defaultPrice(new BigDecimal("10.00"))
                .available(true)
                .build());

        assertEquals(product.getId(),
                productService.getProductById(1L, product.getId()).getId());
        assertEquals(1, productService.getProducts(1L).size());
        assertEquals(0, productService.getProducts(2L).size());

        assertThrows(ProductNotFoundException.class,
                () -> productService.getProductById(2L, product.getId()));
        assertThrows(ProductNotFoundException.class,
                () -> productService.updatePrice(
                        2L,
                        product.getId(),
                        UpdateProductPriceRequest.builder()
                                .defaultPrice(new BigDecimal("20.00"))
                                .build()
                ));
        assertThrows(ProductNotFoundException.class,
                () -> productService.changeAvailability(
                        2L,
                        product.getId(),
                        UpdateProductAvailabilityRequest.builder()
                                .available(false)
                                .build()
                ));

        var unchanged = productService.getProductById(1L, product.getId());
        assertEquals(0, new BigDecimal("10.00").compareTo(unchanged.getDefaultPrice()));
        assertEquals(true, unchanged.getAvailable());
    }
}
