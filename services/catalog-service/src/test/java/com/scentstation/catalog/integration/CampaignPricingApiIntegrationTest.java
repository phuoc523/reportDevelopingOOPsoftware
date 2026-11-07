package com.scentstation.catalog.integration;

import com.scentstation.catalog.entity.Campaign;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.enums.DiscountType;
import com.scentstation.catalog.security.HeaderCurrentActorContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CampaignPricingApiIntegrationTest
        extends CatalogApiIntegrationTestSupport {

    @Test
    void shouldReturnEffectivePriceFromActiveCampaign()
            throws Exception {

        Product product =
                productRepository.save(
                        Product.builder()
                                .tenantId(1L)
                                .name("Rose Garden")
                                .defaultPrice(
                                        new BigDecimal("100000.00")
                                )
                                .available(true)
                                .build()
                );

        LocalDateTime now = LocalDateTime.now();

        campaignRepository.save(
                Campaign.builder()
                        .tenantId(1L)
                        .productId(product.getId())
                        .name("20 Percent Off")
                        .discountType(
                                DiscountType.PERCENTAGE
                        )
                        .discountValue(
                                new BigDecimal("20.00")
                        )
                        .startAt(
                                now.minusDays(1)
                        )
                        .endAt(
                                now.plusDays(1)
                        )
                        .active(true)
                        .build()
        );

        mockMvc.perform(
                        get(
                                "/api/pricing/products/{productId}",
                                product.getId()
                        )
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.defaultPrice")
                                .value(100000.00)
                )
                .andExpect(
                        jsonPath("$.effectivePrice")
                                .value(80000.00)
                )
                .andExpect(
                        jsonPath("$.promotionApplied")
                                .value(true)
                );
    }

    @Test
    void shouldReturnDefaultPriceWhenNoCampaignIsActive()
            throws Exception {

        Product product =
                productRepository.save(
                        Product.builder()
                                .tenantId(1L)
                                .name("Woody Night")
                                .defaultPrice(
                                        new BigDecimal("180000.00")
                                )
                                .available(true)
                                .build()
                );

        mockMvc.perform(
                        get(
                                "/api/pricing/products/{productId}",
                                product.getId()
                        )
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.effectivePrice")
                                .value(180000.00)
                )
                .andExpect(
                        jsonPath("$.promotionApplied")
                                .value(false)
                );
    }
}
