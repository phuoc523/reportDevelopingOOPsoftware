package com.scentstation.catalog.integration;

import com.scentstation.catalog.entity.Campaign;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.enums.DiscountType;
import com.scentstation.catalog.integration.identity.dto.IdentityPrincipal;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CatalogSecurityIntegrationTest extends CatalogApiIntegrationTestSupport {
    private Product product;
    private Campaign campaign;

    @BeforeEach
    void data() {
        product = productRepository.save(Product.builder().tenantId(1L).name("Secured")
                .defaultPrice(new BigDecimal("100.00")).available(true).build());
        LocalDateTime now = LocalDateTime.now();
        campaign = campaignRepository.save(Campaign.builder().tenantId(1L).productId(product.getId())
                .name("Secured campaign").discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN).startAt(now.minusDays(1)).endAt(now.plusDays(1))
                .active(true).build());
    }

    @Test
    void missingOrMalformedBearerIsUnauthorizedAndSpoofedHeadersHaveNoAuthority() throws Exception {
        mockMvc.perform(get("/api/products").header("X-Verified-Tenant-Id", "1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "not-bearer"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void identityRejectedFreshStateIsUnauthorized() throws Exception {
        for (String token : Set.of("invalid", "stale", "suspended")) {
            when(identityIntrospectionClient.introspect(token)).thenThrow(
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or revoked bearer token")
            );
            mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void identityUnavailableFailsClosed() throws Exception {
        when(identityIntrospectionClient.introspect("offline")).thenThrow(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity verification unavailable")
        );
        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer offline"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void catalogReadPermissionIsRequired() throws Exception {
        actor("no-read", Set.of("CATALOG_MANAGE", "CAMPAIGN_MANAGE"), 1L, Set.of("BRAND_ADMIN"));
        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer no-read"))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogManagePermissionIsRequired() throws Exception {
        actor("read-only", Set.of("CATALOG_READ"), 1L, Set.of("VIEWER"));
        mockMvc.perform(patch("/api/products/{id}/price", product.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer read-only")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultPrice\":200}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void campaignManagePermissionIsRequired() throws Exception {
        actor("no-campaign", Set.of("CATALOG_READ", "CATALOG_MANAGE"), 1L, Set.of("BRAND_ADMIN"));
        LocalDateTime now = LocalDateTime.now();
        String body = """
                {"name":"Changed","discountType":"PERCENTAGE","discountValue":15,
                 "startAt":"%s","endAt":"%s","active":true}
                """.formatted(now.minusDays(1), now.plusDays(1));
        mockMvc.perform(put("/api/campaigns/{id}", campaign.getId())
                        .param("tenantId", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer no-campaign")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void validTenantActorWithPermissionsSucceeds() throws Exception {
        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tenantId").value(1));
    }

    @Test
    void platformActorRequiresExplicitTenant() throws Exception {
        actor("platform", Set.of("CATALOG_READ", "CATALOG_MANAGE", "CAMPAIGN_MANAGE"),
                null, Set.of("PLATFORM_SUPER_ADMIN"));
        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer platform"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/campaigns").param("tenantId", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer platform"))
                .andExpect(status().isOk());
    }

    private void actor(String token, Set<String> permissions, Long tenantId, Set<String> roles) {
        when(identityIntrospectionClient.introspect(token)).thenReturn(
                new IdentityPrincipal(99L, token, tenantId, roles, permissions, Set.of(), Set.of())
        );
    }
}
