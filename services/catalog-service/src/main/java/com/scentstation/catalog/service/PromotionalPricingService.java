package com.scentstation.catalog.service;

import com.scentstation.catalog.dto.response.EffectivePriceResponse;
import com.scentstation.catalog.entity.Campaign;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.enums.DiscountType;
import com.scentstation.catalog.exception.ProductNotFoundException;
import com.scentstation.catalog.repository.CampaignRepository;
import com.scentstation.catalog.repository.ProductRepository;
import com.scentstation.catalog.security.CurrentActorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionalPricingService {

    private final ProductRepository productRepository;
    private final CampaignRepository campaignRepository;
    private final CurrentActorContext currentActorContext;

    // Tính giá hiệu lực chỉ cho product thuộc trusted tenant.
    @Transactional(readOnly = true)
    public EffectivePriceResponse getEffectivePrice(
            Long tenantId,
            Long productId
    ) {
        Long verifiedTenantId = currentActorContext.requireTenant(tenantId);
        Product product = productRepository
                .findByIdAndTenantId(productId, verifiedTenantId)
                .orElseThrow(() ->
                        new ProductNotFoundException(productId)
                );

        LocalDateTime now = LocalDateTime.now();

        List<Campaign> campaigns =
                campaignRepository
                        .findAllByTenantIdAndProductIdAndActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
                                verifiedTenantId,
                                productId,
                                now,
                                now
                        );

        if (campaigns.isEmpty()) {
            return EffectivePriceResponse.builder()
                    .tenantId(verifiedTenantId)
                    .productId(productId)
                    .defaultPrice(product.getDefaultPrice())
                    .effectivePrice(product.getDefaultPrice())
                    .promotionApplied(false)
                    .build();
        }

        Campaign selectedCampaign =
                selectBestCampaign(
                        product.getDefaultPrice(),
                        campaigns
                );

        BigDecimal effectivePrice =
                calculateEffectivePrice(
                        product.getDefaultPrice(),
                        selectedCampaign
                );

        return EffectivePriceResponse.builder()
                .tenantId(verifiedTenantId)
                .productId(productId)
                .defaultPrice(product.getDefaultPrice())
                .effectivePrice(effectivePrice)
                .campaignId(selectedCampaign.getId())
                .campaignName(selectedCampaign.getName())
                .discountType(selectedCampaign.getDiscountType())
                .discountValue(selectedCampaign.getDiscountValue())
                .promotionApplied(true)
                .build();
    }

    // Chọn campaign tạo ra mức giá thấp nhất nếu nhiều campaign cùng có hiệu lực.
    private Campaign selectBestCampaign(
            BigDecimal defaultPrice,
            List<Campaign> campaigns
    ) {
        return campaigns.stream()
                .min(
                        Comparator.comparing(
                                campaign ->
                                        calculateEffectivePrice(
                                                defaultPrice,
                                                campaign
                                        )
                        )
                )
                .orElseThrow();
    }

    // Tính giá cuối cùng theo loại discount và không cho giá xuống dưới 0.
    private BigDecimal calculateEffectivePrice(
            BigDecimal defaultPrice,
            Campaign campaign
    ) {
        BigDecimal effectivePrice;

        if (campaign.getDiscountType()
                == DiscountType.PERCENTAGE) {

            BigDecimal percentage =
                    campaign.getDiscountValue()
                            .divide(
                                    BigDecimal.valueOf(100),
                                    4,
                                    RoundingMode.HALF_UP
                            );

            effectivePrice =
                    defaultPrice.subtract(
                            defaultPrice.multiply(percentage)
                    );

        } else {
            effectivePrice =
                    defaultPrice.subtract(
                            campaign.getDiscountValue()
                    );
        }

        if (effectivePrice.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return effectivePrice
                .setScale(2, RoundingMode.HALF_UP);
    }
}
