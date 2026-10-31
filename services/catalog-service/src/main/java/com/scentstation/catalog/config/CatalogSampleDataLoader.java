package com.scentstation.catalog.config;

import com.scentstation.catalog.entity.Campaign;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.enums.DiscountType;
import com.scentstation.catalog.repository.CampaignRepository;
import com.scentstation.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class CatalogSampleDataLoader implements CommandLineRunner {

    private final CatalogSampleDataProperties properties;
    private final ProductRepository productRepository;
    private final CampaignRepository campaignRepository;

    // Nạp dữ liệu mẫu khi catalog.sample-data.enabled được bật.
    @Override
    @Transactional
    public void run(String... args) {
        if (!properties.isEnabled()) {
            return;
        }

        // Không nạp lại nếu database đã có product.
        if (productRepository.count() > 0) {
            return;
        }

        Product floralProduct = productRepository.save(
                Product.builder()
                        .tenantId(1L)
                        .name("Rose Garden")
                        .description("Soft floral fragrance")
                        .fragranceNotes("Rose, musk, vanilla")
                        .imageUrl("https://example.com/images/rose-garden.jpg")
                        .defaultPrice(new BigDecimal("120000.00"))
                        .available(true)
                        .build()
        );

        Product citrusProduct = productRepository.save(
                Product.builder()
                        .tenantId(1L)
                        .name("Citrus Fresh")
                        .description("Fresh citrus fragrance")
                        .fragranceNotes("Orange, lemon, bergamot")
                        .imageUrl("https://example.com/images/citrus-fresh.jpg")
                        .defaultPrice(new BigDecimal("150000.00"))
                        .available(true)
                        .build()
        );

        Product woodyProduct = productRepository.save(
                Product.builder()
                        .tenantId(1L)
                        .name("Woody Night")
                        .description("Warm woody fragrance")
                        .fragranceNotes("Cedar, amber, sandalwood")
                        .imageUrl("https://example.com/images/woody-night.jpg")
                        .defaultPrice(new BigDecimal("180000.00"))
                        .available(true)
                        .build()
        );

        LocalDateTime now = LocalDateTime.now();

        campaignRepository.save(
                Campaign.builder()
                        .tenantId(1L)
                        .productId(floralProduct.getId())
                        .name("Rose Launch 20%")
                        .discountType(DiscountType.PERCENTAGE)
                        .discountValue(new BigDecimal("20.00"))
                        .startAt(now.minusDays(1))
                        .endAt(now.plusDays(30))
                        .active(true)
                        .build()
        );

        campaignRepository.save(
                Campaign.builder()
                        .tenantId(1L)
                        .productId(citrusProduct.getId())
                        .name("Citrus Discount")
                        .discountType(DiscountType.FIXED_AMOUNT)
                        .discountValue(new BigDecimal("20000.00"))
                        .startAt(now.minusDays(1))
                        .endAt(now.plusDays(14))
                        .active(true)
                        .build()
        );

        // woodyProduct hiện không có campaign để sample data bao phủ cả trường hợp giá mặc định.
        woodyProduct.getId();
    }
}