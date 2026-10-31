package com.scentstation.catalog.service;

import com.scentstation.catalog.dto.response.EffectivePriceResponse;
import com.scentstation.catalog.entity.Campaign;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.enums.DiscountType;
import com.scentstation.catalog.exception.ProductNotFoundException;
import com.scentstation.catalog.repository.CampaignRepository;
import com.scentstation.catalog.repository.ProductRepository;
import com.scentstation.catalog.security.CurrentActorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionalPricingServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private CurrentActorContext currentActorContext;

    @InjectMocks
    private PromotionalPricingService promotionalPricingService;

    private Product product;

    @BeforeEach
    void setUp() {
        lenient().when(currentActorContext.requireTenant(1L))
                .thenReturn(1L);

        product = Product.builder()
                .id(10L)
                .tenantId(1L)
                .name("Perfume A")
                .defaultPrice(new BigDecimal("100000.00"))
                .available(true)
                .build();
    }

    @Test
    void getEffectivePrice_shouldReturnDefaultPriceWhenNoCampaignExists() {
        when(productRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(product));

        when(campaignRepository
                .findAllByTenantIdAndProductIdAndActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
                        eq(1L),
                        eq(10L),
                        any(),
                        any()
                ))
                .thenReturn(List.of());

        EffectivePriceResponse response =
                promotionalPricingService.getEffectivePrice(
                        1L,
                        10L
                );

        assertEquals(
                new BigDecimal("100000.00"),
                response.getDefaultPrice()
        );

        assertEquals(
                new BigDecimal("100000.00"),
                response.getEffectivePrice()
        );

        assertFalse(response.getPromotionApplied());
        assertNull(response.getCampaignId());
    }

    @Test
    void getEffectivePrice_shouldApplyPercentageDiscount() {
        Campaign campaign = Campaign.builder()
                .id(100L)
                .tenantId(1L)
                .productId(10L)
                .name("20 Percent Off")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20.00"))
                .active(true)
                .build();

        when(productRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(product));

        when(campaignRepository
                .findAllByTenantIdAndProductIdAndActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
                        eq(1L),
                        eq(10L),
                        any(),
                        any()
                ))
                .thenReturn(List.of(campaign));

        EffectivePriceResponse response =
                promotionalPricingService.getEffectivePrice(
                        1L,
                        10L
                );

        assertEquals(
                new BigDecimal("80000.00"),
                response.getEffectivePrice()
        );

        assertTrue(response.getPromotionApplied());
        assertEquals(100L, response.getCampaignId());
        assertEquals(
                DiscountType.PERCENTAGE,
                response.getDiscountType()
        );
    }

    @Test
    void getEffectivePrice_shouldApplyFixedAmountDiscount() {
        Campaign campaign = Campaign.builder()
                .id(101L)
                .tenantId(1L)
                .productId(10L)
                .name("Fixed Discount")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("15000.00"))
                .active(true)
                .build();

        when(productRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(product));

        when(campaignRepository
                .findAllByTenantIdAndProductIdAndActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
                        eq(1L),
                        eq(10L),
                        any(),
                        any()
                ))
                .thenReturn(List.of(campaign));

        EffectivePriceResponse response =
                promotionalPricingService.getEffectivePrice(
                        1L,
                        10L
                );

        assertEquals(
                new BigDecimal("85000.00"),
                response.getEffectivePrice()
        );
    }

    @Test
    void getEffectivePrice_shouldChooseCampaignWithLowestEffectivePrice() {
        Campaign percentageCampaign = Campaign.builder()
                .id(100L)
                .tenantId(1L)
                .productId(10L)
                .name("20 Percent")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20.00"))
                .active(true)
                .build();

        Campaign fixedCampaign = Campaign.builder()
                .id(101L)
                .tenantId(1L)
                .productId(10L)
                .name("Thirty Thousand Off")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("30000.00"))
                .active(true)
                .build();

        when(productRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(product));

        when(campaignRepository
                .findAllByTenantIdAndProductIdAndActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
                        eq(1L),
                        eq(10L),
                        any(),
                        any()
                ))
                .thenReturn(
                        List.of(
                                percentageCampaign,
                                fixedCampaign
                        )
                );

        EffectivePriceResponse response =
                promotionalPricingService.getEffectivePrice(
                        1L,
                        10L
                );

        assertEquals(
                new BigDecimal("70000.00"),
                response.getEffectivePrice()
        );

        assertEquals(101L, response.getCampaignId());
    }

    @Test
    void getEffectivePrice_shouldNeverReturnNegativePrice() {
        Campaign campaign = Campaign.builder()
                .id(100L)
                .tenantId(1L)
                .productId(10L)
                .name("Huge Discount")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("150000.00"))
                .active(true)
                .build();

        when(productRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(product));

        when(campaignRepository
                .findAllByTenantIdAndProductIdAndActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
                        eq(1L),
                        eq(10L),
                        any(),
                        any()
                ))
                .thenReturn(List.of(campaign));

        EffectivePriceResponse response =
                promotionalPricingService.getEffectivePrice(
                        1L,
                        10L
                );

        assertEquals(
                new BigDecimal("0.00"),
                response.getEffectivePrice()
        );
    }

    @Test
    void getEffectivePrice_shouldRejectProductFromAnotherTenant() {
        product.setTenantId(2L);

        when(productRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(
                ProductNotFoundException.class,
                () ->
                        promotionalPricingService
                                .getEffectivePrice(1L, 10L)
        );
    }
}
