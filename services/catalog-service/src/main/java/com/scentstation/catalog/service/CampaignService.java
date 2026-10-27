package com.scentstation.catalog.service;

import com.scentstation.catalog.dto.request.CreateCampaignRequest;
import com.scentstation.catalog.dto.request.UpdateCampaignRequest;
import com.scentstation.catalog.dto.response.CampaignResponse;
import com.scentstation.catalog.entity.Campaign;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.enums.DiscountType;
import com.scentstation.catalog.exception.CampaignNotFoundException;
import com.scentstation.catalog.exception.InvalidCampaignPeriodException;
import com.scentstation.catalog.exception.InvalidDiscountValueException;
import com.scentstation.catalog.exception.ProductNotFoundException;
import com.scentstation.catalog.mapper.CampaignMapper;
import com.scentstation.catalog.repository.CampaignRepository;
import com.scentstation.catalog.repository.ProductRepository;
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

    // Tạo campaign mới sau khi kiểm tra product, tenant, thời gian và mức giảm hợp lệ.
    @Transactional
    public CampaignResponse createCampaign(
            CreateCampaignRequest request
    ) {
        Product product = productRepository
                .findById(request.getProductId())
                .orElseThrow(() ->
                        new ProductNotFoundException(
                                request.getProductId()
                        )
                );

        validateProductTenant(
                product,
                request.getTenantId()
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
                campaignMapper.toEntity(request);

        Campaign savedCampaign =
                campaignRepository.save(campaign);

        return campaignMapper.toResponse(savedCampaign);
    }

    // Lấy toàn bộ campaign thuộc một tenant.
    @Transactional(readOnly = true)
    public List<CampaignResponse> getCampaigns(
            Long tenantId
    ) {
        return campaignRepository
                .findAllByTenantId(tenantId)
                .stream()
                .map(campaignMapper::toResponse)
                .toList();
    }

    // Lấy campaign theo id và tenant để giữ tenant isolation.
    @Transactional(readOnly = true)
    public CampaignResponse getCampaignById(
            Long tenantId,
            Long campaignId
    ) {
        Campaign campaign =
                findCampaign(
                        tenantId,
                        campaignId
                );

        return campaignMapper.toResponse(campaign);
    }

    // Lấy toàn bộ campaign của một product trong một tenant.
    @Transactional(readOnly = true)
    public List<CampaignResponse> getCampaignsByProduct(
            Long tenantId,
            Long productId
    ) {
        return campaignRepository
                .findAllByTenantIdAndProductId(
                        tenantId,
                        productId
                )
                .stream()
                .map(campaignMapper::toResponse)
                .toList();
    }

    // Cập nhật các thuộc tính được phép thay đổi của campaign.
    @Transactional
    public CampaignResponse updateCampaign(
            Long tenantId,
            Long campaignId,
            UpdateCampaignRequest request
    ) {
        Campaign campaign =
                findCampaign(
                        tenantId,
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

    // Kiểm tra product phải thuộc cùng tenant với campaign.
    private void validateProductTenant(
            Product product,
            Long tenantId
    ) {
        if (!product.getTenantId().equals(tenantId)) {
            throw new ProductNotFoundException(
                    product.getId()
            );
        }
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