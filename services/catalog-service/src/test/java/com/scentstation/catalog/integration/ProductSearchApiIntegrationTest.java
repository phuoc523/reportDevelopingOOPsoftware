package com.scentstation.catalog.integration;

import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.security.HeaderCurrentActorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductSearchApiIntegrationTest
        extends CatalogApiIntegrationTestSupport {

    @BeforeEach
    void prepareProducts() {
        productRepository.save(
                Product.builder()
                        .tenantId(1L)
                        .name("Rose Garden")
                        .description("Soft floral fragrance")
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
                        .description("Fresh aquatic fragrance")
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
                        .name("Rose Other Tenant")
                        .description("Hidden from tenant 1")
                        .fragranceNotes("Rose")
                        .defaultPrice(
                                new BigDecimal("220000.00")
                        )
                        .available(true)
                        .build()
        );
    }

    @Test
    void shouldSearchByTenantKeywordAndAvailability()
            throws Exception {

        mockMvc.perform(
                        get("/api/products/search")
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                .param("tenantId", "1")
                                .param("keyword", "rose")
                                .param("available", "true")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$[0].name")
                                .value("Rose Garden")
                );
    }

    @Test
    void shouldFilterByPriceRange()
            throws Exception {

        mockMvc.perform(
                        get("/api/products/search")
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                .param("tenantId", "1")
                                .param(
                                        "minPrice",
                                        "300000"
                                )
                                .param(
                                        "maxPrice",
                                        "400000"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$[0].name")
                                .value("Ocean Blue")
                );
    }

    @Test
    void shouldUseTrustedTenantWhenQueryTenantIsMissing()
            throws Exception {

        mockMvc.perform(
                        get("/api/products/search")
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                .param("keyword", "rose")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Rose Garden"));
    }
}
