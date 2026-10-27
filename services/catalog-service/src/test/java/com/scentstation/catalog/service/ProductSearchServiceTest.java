package com.scentstation.catalog.service;

import com.scentstation.catalog.dto.request.ProductSearchCriteria;
import com.scentstation.catalog.exception.InvalidProductSearchCriteriaException;
import com.scentstation.catalog.mapper.ProductMapper;
import com.scentstation.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ProductSearchServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    @Test
    void searchProducts_shouldRejectNegativeMinimumPrice() {
        ProductSearchCriteria criteria =
                new ProductSearchCriteria();

        criteria.setMinPrice(
                new BigDecimal("-1.00")
        );

        assertThrows(
                InvalidProductSearchCriteriaException.class,
                () ->
                        productService.searchProducts(criteria)
        );

        verify(productRepository, never())
                .findAll(
                        any(
                                org.springframework.data.jpa.domain.Specification.class
                        )
                );
    }

    @Test
    void searchProducts_shouldRejectNegativeMaximumPrice() {
        ProductSearchCriteria criteria =
                new ProductSearchCriteria();

        criteria.setMaxPrice(
                new BigDecimal("-1.00")
        );

        assertThrows(
                InvalidProductSearchCriteriaException.class,
                () ->
                        productService.searchProducts(criteria)
        );
    }

    @Test
    void searchProducts_shouldRejectReversedPriceRange() {
        ProductSearchCriteria criteria =
                new ProductSearchCriteria();

        criteria.setMinPrice(
                new BigDecimal("500000.00")
        );

        criteria.setMaxPrice(
                new BigDecimal("100000.00")
        );

        assertThrows(
                InvalidProductSearchCriteriaException.class,
                () ->
                        productService.searchProducts(criteria)
        );
    }
}