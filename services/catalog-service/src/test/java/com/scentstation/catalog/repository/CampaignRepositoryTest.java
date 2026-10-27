package com.scentstation.catalog.repository;

import com.scentstation.catalog.entity.Campaign;
import com.scentstation.catalog.enums.DiscountType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class CampaignRepositoryTest {

    @Autowired
    private CampaignRepository campaignRepository;

    @Test
    void shouldReturnOnlyCurrentlyActiveCampaigns() {
        LocalDateTime now = LocalDateTime.now();

        Campaign activeCampaign =
                Campaign.builder()
                        .tenantId(1L)
                        .productId(10L)
                        .name("Active")
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
                        .build();

        Campaign expiredCampaign =
                Campaign.builder()
                        .tenantId(1L)
                        .productId(10L)
                        .name("Expired")
                        .discountType(
                                DiscountType.PERCENTAGE
                        )
                        .discountValue(
                                new BigDecimal("10.00")
                        )
                        .startAt(
                                now.minusDays(5)
                        )
                        .endAt(
                                now.minusDays(1)
                        )
                        .active(true)
                        .build();

        Campaign disabledCampaign =
                Campaign.builder()
                        .tenantId(1L)
                        .productId(10L)
                        .name("Disabled")
                        .discountType(
                                DiscountType.PERCENTAGE
                        )
                        .discountValue(
                                new BigDecimal("30.00")
                        )
                        .startAt(
                                now.minusDays(1)
                        )
                        .endAt(
                                now.plusDays(1)
                        )
                        .active(false)
                        .build();

        campaignRepository.save(activeCampaign);
        campaignRepository.save(expiredCampaign);
        campaignRepository.save(disabledCampaign);

        List<Campaign> result =
                campaignRepository
                        .findAllByTenantIdAndProductIdAndActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
                                1L,
                                10L,
                                now,
                                now
                        );

        assertEquals(1, result.size());

        assertEquals(
                "Active",
                result.getFirst().getName()
        );
    }
}