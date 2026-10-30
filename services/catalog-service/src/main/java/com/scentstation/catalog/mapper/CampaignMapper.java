package com.scentstation.catalog.mapper;

import com.scentstation.catalog.dto.request.CreateCampaignRequest;
import com.scentstation.catalog.dto.request.UpdateCampaignRequest;
import com.scentstation.catalog.dto.response.CampaignResponse;
import com.scentstation.catalog.entity.Campaign;
import org.springframework.stereotype.Component;

@Component
public class CampaignMapper {

    // Chuyển request tạo campaign thành entity để lưu.
    public Campaign toEntity(CreateCampaignRequest request) {
        return Campaign.builder()
                .tenantId(request.getTenantId())
                .productId(request.getProductId())
                .name(request.getName())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .active(
                        request.getActive() != null
                                ? request.getActive()
                                : true
                )
                .build();
    }

    // Chuyển campaign entity thành response gửi cho client.
    public CampaignResponse toResponse(Campaign campaign) {
        return CampaignResponse.builder()
                .id(campaign.getId())
                .tenantId(campaign.getTenantId())
                .productId(campaign.getProductId())
                .name(campaign.getName())
                .discountType(campaign.getDiscountType())
                .discountValue(campaign.getDiscountValue())
                .startAt(campaign.getStartAt())
                .endAt(campaign.getEndAt())
                .active(campaign.getActive())
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt())
                .build();
    }

    // Cập nhật các field được phép thay đổi của campaign hiện có.
    public void updateEntity(
            UpdateCampaignRequest request,
            Campaign campaign
    ) {
        campaign.setName(request.getName());
        campaign.setDiscountType(request.getDiscountType());
        campaign.setDiscountValue(request.getDiscountValue());
        campaign.setStartAt(request.getStartAt());
        campaign.setEndAt(request.getEndAt());
        campaign.setActive(request.getActive());
    }
}