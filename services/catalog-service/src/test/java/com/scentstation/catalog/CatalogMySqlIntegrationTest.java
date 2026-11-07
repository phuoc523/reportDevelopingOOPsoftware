package com.scentstation.catalog;

import com.scentstation.catalog.dto.request.CreateProductRequest;
import com.scentstation.catalog.dto.request.UpdateProductAvailabilityRequest;
import com.scentstation.catalog.dto.request.UpdateProductPriceRequest;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.exception.ProductNotFoundException;
import com.scentstation.catalog.repository.ProductRepository;
import com.scentstation.catalog.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CatalogMySqlIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.41")
            .withDatabaseName("scentstation_catalog")
            .withUsername("catalog")
            .withPassword("catalog");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void flywayBootstrapsSchemaAndTenantIsolationUsesRealMySql() {
        var product = productService.createProduct(CreateProductRequest.builder()
                .tenantId(1L)
                .name("MySQL product")
                .defaultPrice(new BigDecimal("99.99"))
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
                                .defaultPrice(BigDecimal.ONE)
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
    }

    @Test
    void databaseRejectsNegativePrice() {
        Product product = Product.builder()
                .tenantId(1L)
                .name("Invalid price")
                .defaultPrice(new BigDecimal("-0.01"))
                .available(true)
                .build();

        assertThrows(JpaSystemException.class,
                () -> productRepository.saveAndFlush(product));
    }
}
