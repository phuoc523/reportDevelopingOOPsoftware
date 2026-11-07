package com.scentstation.catalog.service;

import com.scentstation.catalog.dto.request.CreateCampaignRequest;
import com.scentstation.catalog.dto.request.UpdateCampaignRequest;
import com.scentstation.catalog.dto.response.CampaignResponse;
import com.scentstation.catalog.entity.Campaign;
import com.scentstation.catalog.enums.DiscountType;
import com.scentstation.catalog.exception.CampaignNotFoundException;
import com.scentstation.catalog.exception.InvalidCampaignPeriodException;
import com.scentstation.catalog.exception.InvalidDiscountValueException;
import com.scentstation.catalog.exception.ProductNotFoundException;
import com.scentstation.catalog.mapper.CampaignMapper;
import com.scentstation.catalog.repository.CampaignRepository;
import com.scentstation.catalog.repository.ProductRepository;
import com.scentstation.catalog.security.CurrentActorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final ProductRepository productRepository;
    private final CampaignMapper campaignMapper;
    private final CurrentActorContext currentActorContext;

    // Tạo campaign bằng trusted tenant và chỉ cho phép product cùng tenant.
    @Transactional
    public CampaignResponse createCampaign(
            CreateCampaignRequest request
    ) {
        Long tenantId = currentActorContext.requireTenant(request.getTenantId());
        productRepository
                .findByIdAndTenantId(request.getProductId(), tenantId)
                .orElseThrow(() ->
                        new ProductNotFoundException(
                                request.getProductId()
                        )
                );

        validatePeriod(
                request.getStartAt(),
                request.getEndAt()
        );

        validateDiscount(
                request.getDiscountType(),
                request.getDiscountValue()
        );

        Campaign campaign =
                campaignMapper.toEntity(request, tenantId);

        Campaign savedCampaign =
                campaignRepository.save(campaign);

        return campaignMapper.toResponse(savedCampaign);
    }

    // Chỉ lấy campaign của trusted tenant và từ chối query tenant khác.
    @Transactional(readOnly = true)
    public List<CampaignResponse> getCampaigns(
            Long tenantId
    ) {
        Long verifiedTenantId = currentActorContext.requireTenant(tenantId);
        return campaignRepository
                .findAllByTenantId(verifiedTenantId)
                .stream()
                .map(campaignMapper::toResponse)
                .toList();
    }

    // Chỉ lấy campaign theo id trong trusted tenant.
    @Transactional(readOnly = true)
    public CampaignResponse getCampaignById(
            Long tenantId,
            Long campaignId
    ) {
        Long verifiedTenantId = currentActorContext.requireTenant(tenantId);
        Campaign campaign =
                findCampaign(
                        verifiedTenantId,
                        campaignId
                );

        return campaignMapper.toResponse(campaign);
    }

    // Chỉ lấy campaign khi product thuộc trusted tenant.
    @Transactional(readOnly = true)
    public List<CampaignResponse> getCampaignsByProduct(
            Long tenantId,
            Long productId
    ) {
        Long verifiedTenantId = currentActorContext.requireTenant(tenantId);
        productRepository
                .findByIdAndTenantId(productId, verifiedTenantId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        return campaignRepository
                .findAllByTenantIdAndProductId(
                        verifiedTenantId,
                        productId
                )
                .stream()
                .map(campaignMapper::toResponse)
                .toList();
    }

    // Chỉ cập nhật campaign thuộc trusted tenant.
    @Transactional
    public CampaignResponse updateCampaign(
            Long tenantId,
            Long campaignId,
            UpdateCampaignRequest request
    ) {
        Long verifiedTenantId = currentActorContext.requireTenant(tenantId);
        Campaign campaign =
                findCampaign(
                        verifiedTenantId,
                        campaignId
                );

        validatePeriod(
                request.getStartAt(),
                request.getEndAt()
        );

        validateDiscount(
                request.getDiscountType(),
                request.getDiscountValue()
        );

        campaignMapper.updateEntity(
                request,
                campaign
        );

        Campaign updatedCampaign =
                campaignRepository.save(campaign);

        return campaignMapper.toResponse(
                updatedCampaign
        );
    }

    // Tìm campaign theo id và tenant hoặc báo lỗi nếu không tồn tại.
    private Campaign findCampaign(
            Long tenantId,
            Long campaignId
    ) {
        return campaignRepository
                .findByIdAndTenantId(
                        campaignId,
                        tenantId
                )
                .orElseThrow(() ->
                        new CampaignNotFoundException(
                                campaignId,
                                tenantId
                        )
                );
    }

    // Kiểm tra thời điểm bắt đầu phải nằm trước thời điểm kết thúc.
    private void validatePeriod(
            java.time.LocalDateTime startAt,
            java.time.LocalDateTime endAt
    ) {
        if (!startAt.isBefore(endAt)) {
            throw new InvalidCampaignPeriodException();
        }
    }

    // Kiểm tra giá trị giảm phù hợp với loại discount.
    private void validateDiscount(
            DiscountType discountType,
            BigDecimal discountValue
    ) {
        if (discountValue == null
                || discountValue.compareTo(BigDecimal.ZERO) <= 0) {

            throw new InvalidDiscountValueException(
                    "Discount value must be greater than zero"
            );
        }

        if (discountType == DiscountType.PERCENTAGE
                && discountValue.compareTo(
                        BigDecimal.valueOf(100)
                ) > 0) {

            throw new InvalidDiscountValueException(
                    "Percentage discount must not exceed 100"
            );
        }
    }
}
