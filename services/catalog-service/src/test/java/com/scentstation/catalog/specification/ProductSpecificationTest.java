package com.scentstation.catalog.specification;

import com.scentstation.catalog.dto.request.ProductSearchCriteria;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class ProductSpecificationTest {

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.save(
                Product.builder()
                        .tenantId(1L)
                        .name("Rose Garden")
                        .description("Soft floral perfume")
                        .fragranceNotes("Rose, musk")
                        .defaultPrice(
                                new BigDecimal("200000.00")
                        )
                        .available(true)
                        .build()
        );

        productRepository.save(
                Product.builder()
                        .tenantId(1L)
                        .name("Ocean Blue")
                        .description("Fresh aquatic perfume")
                        .fragranceNotes("Marine, citrus")
                        .defaultPrice(
                                new BigDecimal("350000.00")
                        )
                        .available(false)
                        .build()
        );

        productRepository.save(
                Product.builder()
                        .tenantId(2L)
                        .name("Rose Premium")
                        .description("Rose perfume from another tenant")
                        .fragranceNotes("Rose")
                        .defaultPrice(
                                new BigDecimal("250000.00")
                        )
                        .available(true)
                        .build()
        );
    }

    @Test
    void shouldFilterProductsByTenant() {
        ProductSearchCriteria criteria =
                new ProductSearchCriteria();

        criteria.setTenantId(1L);

        List<Product> result =
                productRepository.findAll(
                        ProductSpecification.from(criteria)
                );

        assertEquals(2, result.size());
    }

    @Test
    void shouldSearchKeywordIgnoringCase() {
        ProductSearchCriteria criteria =
                new ProductSearchCriteria();

        criteria.setTenantId(1L);
        criteria.setKeyword("ROSE");

        List<Product> result =
                productRepository.findAll(
                        ProductSpecification.from(criteria)
                );

        assertEquals(1, result.size());
        assertEquals(
                "Rose Garden",
                result.getFirst().getName()
        );
    }

    @Test
    void shouldSearchKeywordInsideFragranceNotes() {
        ProductSearchCriteria criteria =
                new ProductSearchCriteria();

        criteria.setTenantId(1L);
        criteria.setKeyword("citrus");

        List<Product> result =
                productRepository.findAll(
                        ProductSpecification.from(criteria)
                );

        assertEquals(1, result.size());
        assertEquals(
                "Ocean Blue",
                result.getFirst().getName()
        );
    }

    @Test
    void shouldFilterByAvailability() {
        ProductSearchCriteria criteria =
                new ProductSearchCriteria();

        criteria.setTenantId(1L);
        criteria.setAvailable(true);

        List<Product> result =
                productRepository.findAll(
                        ProductSpecification.from(criteria)
                );

        assertEquals(1, result.size());
        assertEquals(
                "Rose Garden",
                result.getFirst().getName()
        );
    }

    @Test
    void shouldFilterByPriceRange() {
        ProductSearchCriteria criteria =
                new ProductSearchCriteria();

        criteria.setTenantId(1L);

        criteria.setMinPrice(
                new BigDecimal("300000.00")
        );

        criteria.setMaxPrice(
                new BigDecimal("400000.00")
        );

        List<Product> result =
                productRepository.findAll(
                        ProductSpecification.from(criteria)
                );

        assertEquals(1, result.size());

        assertEquals(
                "Ocean Blue",
                result.getFirst().getName()
        );
    }

    @Test
    void shouldCombineMultipleFilters() {
        ProductSearchCriteria criteria =
                new ProductSearchCriteria();

        criteria.setTenantId(1L);
        criteria.setKeyword("rose");
        criteria.setAvailable(true);

        criteria.setMinPrice(
                new BigDecimal("100000.00")
        );

        criteria.setMaxPrice(
                new BigDecimal("250000.00")
        );

        List<Product> result =
                productRepository.findAll(
                        ProductSpecification.from(criteria)
                );

        assertEquals(1, result.size());

        assertEquals(
                "Rose Garden",
                result.getFirst().getName()
        );
    }
}