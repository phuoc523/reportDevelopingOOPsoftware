package com.scentstation.catalog.controller;

import com.scentstation.catalog.dto.request.CreateCampaignRequest;
import com.scentstation.catalog.dto.request.UpdateCampaignRequest;
import com.scentstation.catalog.dto.response.CampaignResponse;
import com.scentstation.catalog.service.CampaignService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@Validated
public class CampaignController {

    private final CampaignService campaignService;

    // Tạo một campaign mới.
    @PostMapping
    public ResponseEntity<CampaignResponse> createCampaign(
            @Valid @RequestBody CreateCampaignRequest request
    ) {
        CampaignResponse response =
                campaignService.createCampaign(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // Trả về toàn bộ campaign của một tenant.
    @GetMapping
    public ResponseEntity<List<CampaignResponse>> getCampaigns(
            @RequestParam
            @Positive
            Long tenantId
    ) {
        return ResponseEntity.ok(
                campaignService.getCampaigns(tenantId)
        );
    }

    // Trả về một campaign theo id và tenant.
    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> getCampaignById(
            @PathVariable
            @Positive
            Long campaignId,

            @RequestParam
            @Positive
            Long tenantId
    ) {
        return ResponseEntity.ok(
                campaignService.getCampaignById(
                        tenantId,
                        campaignId
                )
        );
    }

    // Trả về các campaign của một sản phẩm trong tenant.
    @GetMapping("/products/{productId}")
    public ResponseEntity<List<CampaignResponse>>
    getCampaignsByProduct(
            @PathVariable
            @Positive
            Long productId,

            @RequestParam
            @Positive
            Long tenantId
    ) {
        return ResponseEntity.ok(
                campaignService.getCampaignsByProduct(
                        tenantId,
                        productId
                )
        );
    }

    // Cập nhật campaign hiện có mà không thay tenant hoặc product.
    @PutMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> updateCampaign(
            @PathVariable
            @Positive
            Long campaignId,

            @RequestParam
            @Positive
            Long tenantId,

            @Valid
            @RequestBody
            UpdateCampaignRequest request
    ) {
        return ResponseEntity.ok(
                campaignService.updateCampaign(
                        tenantId,
                        campaignId,
                        request
                )
        );
    }
}