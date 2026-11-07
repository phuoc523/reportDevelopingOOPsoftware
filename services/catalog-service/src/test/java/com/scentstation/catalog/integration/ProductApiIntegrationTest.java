package com.scentstation.catalog.integration;

import com.scentstation.catalog.dto.request.CreateProductRequest;
import com.scentstation.catalog.security.HeaderCurrentActorContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductApiIntegrationTest
        extends CatalogApiIntegrationTestSupport {

    @Test
    void shouldCreateAndReadProduct() throws Exception {
        CreateProductRequest request =
                new CreateProductRequest();

        request.setTenantId(1L);
        request.setName("Rose Garden");
        request.setDescription("Soft floral fragrance");
        request.setFragranceNotes("Rose, musk");
        request.setImageUrl("https://example.com/rose.jpg");
        request.setDefaultPrice(
                new BigDecimal("120000.00")
        );
        request.setAvailable(true);

        String response =
                mockMvc.perform(
                                post("/api/products")
                                        .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        request
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andExpect(
                                jsonPath("$.name")
                                        .value("Rose Garden")
                        )
                        .andExpect(
                                jsonPath("$.tenantId")
                                        .value(1)
                        )
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        long productId =
                objectMapper
                        .readTree(response)
                        .get("id")
                        .asLong();

        mockMvc.perform(
                        get(
                                "/api/products/{id}",
                                productId
                        )
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(productId)
                )
                .andExpect(
                        jsonPath("$.defaultPrice")
                                .value(120000.00)
                );
    }

    @Test
    void shouldUpdateProductPrice() throws Exception {
        var product =
                productRepository.save(
                        com.scentstation.catalog.entity.Product.builder()
                                .tenantId(1L)
                                .name("Product A")
                                .defaultPrice(
                                        new BigDecimal("100000.00")
                                )
                                .available(true)
                                .build()
                );

        String request = """
                {
                  "defaultPrice": 150000.00
                }
                """;

        mockMvc.perform(
                        patch(
                                "/api/products/{id}/price",
                                product.getId()
                        )
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(request)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.defaultPrice")
                                .value(150000.00)
                );
    }
}
