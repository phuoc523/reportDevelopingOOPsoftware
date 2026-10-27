package com.scentstation.catalog.service;

import com.scentstation.catalog.dto.response.EffectivePriceResponse;
import com.scentstation.catalog.entity.Campaign;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.enums.DiscountType;
import com.scentstation.catalog.exception.ProductNotFoundException;
import com.scentstation.catalog.repository.CampaignRepository;
import com.scentstation.catalog.repository.ProductRepository;
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

    // Tính giá hiệu lực hiện tại của sản phẩm dựa trên campaign đang active.
    @Transactional(readOnly = true)
    public EffectivePriceResponse getEffectivePrice(
            Long tenantId,
            Long productId
    ) {
        Product product = productRepository
                .findById(productId)
                .orElseThrow(() ->
                        new ProductNotFoundException(productId)
                );

        // Không để tenant khác đọc pricing của product không thuộc mình.
        if (!product.getTenantId().equals(tenantId)) {
            throw new ProductNotFoundException(productId);
        }

        LocalDateTime now = LocalDateTime.now();

        List<Campaign> campaigns =
                campaignRepository
                        .findAllByTenantIdAndProductIdAndActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
                                tenantId,
                                productId,
                                now,
                                now
                        );

        if (campaigns.isEmpty()) {
            return EffectivePriceResponse.builder()
                    .tenantId(tenantId)
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
                .tenantId(tenantId)
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