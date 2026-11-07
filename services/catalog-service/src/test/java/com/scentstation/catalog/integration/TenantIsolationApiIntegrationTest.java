package com.scentstation.catalog.integration;

import com.scentstation.catalog.dto.request.CreateProductRequest;
import com.scentstation.catalog.entity.Campaign;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.enums.DiscountType;
import com.scentstation.catalog.security.HeaderCurrentActorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TenantIsolationApiIntegrationTest
        extends CatalogApiIntegrationTestSupport {

    private Product tenantOneProduct;
    private Product tenantTwoProduct;
    private Campaign tenantTwoCampaign;

    // Tạo dữ liệu của hai tenant để kiểm tra các đường truy cập chéo tenant.
    @BeforeEach
    void prepareTenantData() {
        tenantOneProduct = saveProduct(1L, "Tenant One Product");
        tenantTwoProduct = saveProduct(2L, "Tenant Two Product");

        LocalDateTime now = LocalDateTime.now();
        tenantTwoCampaign = campaignRepository.save(
                Campaign.builder()
                        .tenantId(2L)
                        .productId(tenantTwoProduct.getId())
                        .name("Tenant Two Campaign")
                        .discountType(DiscountType.PERCENTAGE)
                        .discountValue(new BigDecimal("10.00"))
                        .startAt(now.minusDays(1))
                        .endAt(now.plusDays(1))
                        .active(true)
                        .build()
        );
    }

    // Chỉ lưu Product test với tenant được chỉ định.
    private Product saveProduct(Long tenantId, String name) {
        return productRepository.save(
                Product.builder()
                        .tenantId(tenantId)
                        .name(name)
                        .defaultPrice(new BigDecimal("100000.00"))
                        .available(true)
                        .build()
        );
    }

    // Chứng minh list của tenant 1 không lộ product tenant 2.
    @Test
    void tenantOneList_shouldNotContainTenantTwoProduct() throws Exception {
        mockMvc.perform(
                        get("/api/products")
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(tenantOneProduct.getId()));
    }

    // Chứng minh tenant 1 nhận 404 khi đọc product tenant 2.
    @Test
    void tenantOneGet_shouldHideTenantTwoProduct() throws Exception {
        mockMvc.perform(
                        get("/api/products/{id}", tenantTwoProduct.getId())
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                )
                .andExpect(status().isNotFound());
    }

    // Chứng minh tenant 1 không thể sửa giá product tenant 2.
    @Test
    void tenantOnePricePatch_shouldNotChangeTenantTwoProduct() throws Exception {
        mockMvc.perform(
                        patch("/api/products/{id}/price", tenantTwoProduct.getId())
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"defaultPrice\": 250000.00}")
                )
                .andExpect(status().isNotFound());

        Product unchanged = productRepository.findById(tenantTwoProduct.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(
                new BigDecimal("100000.00"),
                unchanged.getDefaultPrice()
        );
    }

    // Chứng minh tenant 1 không thể sửa availability của product tenant 2.
    @Test
    void tenantOneAvailabilityPatch_shouldNotChangeTenantTwoProduct() throws Exception {
        mockMvc.perform(
                        patch("/api/products/{id}/availability", tenantTwoProduct.getId())
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"available\": false}")
                )
                .andExpect(status().isNotFound());

        Product unchanged = productRepository.findById(tenantTwoProduct.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(unchanged.getAvailable());
    }

    // Chứng minh body create không thể giả tenant khác trusted tenant.
    @Test
    void createProduct_shouldRejectSpoofedTenant() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .tenantId(2L)
                .name("Spoofed Product")
                .defaultPrice(new BigDecimal("100000.00"))
                .available(true)
                .build();

        mockMvc.perform(
                        post("/api/products")
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());
    }

    // Chứng minh query search không thể override trusted tenant.
    @Test
    void search_shouldRejectTenantOverride() throws Exception {
        mockMvc.perform(
                        get("/api/products/search")
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                .param("tenantId", "2")
                )
                .andExpect(status().isBadRequest());
    }

    // Chứng minh tenant 1 nhận 404 khi đọc campaign tenant 2.
    @Test
    void tenantOneGet_shouldHideTenantTwoCampaign() throws Exception {
        mockMvc.perform(
                        get("/api/campaigns/{campaignId}", tenantTwoCampaign.getId())
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                .param("tenantId", "1")
                )
                .andExpect(status().isNotFound());
    }

    // Chứng minh tenant 1 không thể cập nhật campaign tenant 2.
    @Test
    void tenantOneUpdate_shouldHideTenantTwoCampaign() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String request = """
                {
                  "name": "Changed",
                  "discountType": "PERCENTAGE",
                  "discountValue": 20,
                  "startAt": "%s",
                  "endAt": "%s",
                  "active": true
                }
                """.formatted(now.minusDays(1), now.plusDays(1));

        mockMvc.perform(
                        put("/api/campaigns/{campaignId}", tenantTwoCampaign.getId())
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                .param("tenantId", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isNotFound());
    }

    // Chứng minh tenant 1 không thể query campaign qua product tenant 2.
    @Test
    void tenantOneCampaignQuery_shouldHideTenantTwoProduct() throws Exception {
        mockMvc.perform(
                        get("/api/campaigns/products/{productId}", tenantTwoProduct.getId())
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                .param("tenantId", "1")
                )
                .andExpect(status().isNotFound());
    }

    // Chứng minh tenant 1 không thể đọc effective price của product tenant 2.
    @Test
    void tenantOnePricing_shouldHideTenantTwoProduct() throws Exception {
        mockMvc.perform(
                        get("/api/pricing/products/{productId}", tenantTwoProduct.getId())
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                .param("tenantId", "1")
                )
                .andExpect(status().isNotFound());
    }

    // Chứng minh tenant 1 không thể query machine availability cho product tenant 2.
    @Test
    void tenantOneMachineAvailability_shouldHideTenantTwoProduct() throws Exception {
        mockMvc.perform(
                        get(
                                "/api/catalog/availability/machines/{machineId}/products/{productId}",
                                3L,
                                tenantTwoProduct.getId()
                        )
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                .param("tenantId", "1")
                )
                .andExpect(status().isNotFound());
    }

    // Chứng minh request thiếu trusted tenant bị từ chối tại HTTP boundary.
    @Test
    void requestWithoutTrustedTenant_shouldBeUnauthorized() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }
}
