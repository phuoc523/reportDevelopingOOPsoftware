package com.scentstation.catalog.controller;

import com.scentstation.catalog.dto.response.EffectivePriceResponse;
import com.scentstation.catalog.enums.DiscountType;
import com.scentstation.catalog.service.PromotionalPricingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PromotionalPricingController.class)
class PromotionalPricingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PromotionalPricingService promotionalPricingService;

    @Test
    void getEffectivePrice_shouldReturnPricing()
            throws Exception {

        EffectivePriceResponse response =
                EffectivePriceResponse.builder()
                        .tenantId(1L)
                        .productId(10L)
                        .defaultPrice(
                                new BigDecimal("100000.00")
                        )
                        .effectivePrice(
                                new BigDecimal("80000.00")
                        )
                        .campaignId(100L)
                        .campaignName("20 Percent Off")
                        .discountType(
                                DiscountType.PERCENTAGE
                        )
                        .discountValue(
                                new BigDecimal("20.00")
                        )
                        .promotionApplied(true)
                        .build();

        when(promotionalPricingService
                .getEffectivePrice(1L, 10L))
                .thenReturn(response);

        mockMvc.perform(
                        get(
                                "/api/pricing/products/{productId}",
                                10L
                        )
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
                )
                .andExpect(
                        jsonPath("$.campaignId")
                                .value(100)
                );
    }
}