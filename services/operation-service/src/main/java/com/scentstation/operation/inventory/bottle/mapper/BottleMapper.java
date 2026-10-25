package com.scentstation.operation.inventory.bottle.mapper;

import com.scentstation.operation.inventory.bottle.dto.request.CreateBottleRequest;
import com.scentstation.operation.inventory.bottle.dto.response.BottleResponse;
import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import org.springframework.stereotype.Component;

@Component
public class BottleMapper {

    public Bottle toEntity(CreateBottleRequest request) {
        return Bottle.builder()
                .tenantId(request.getTenantId())
                .productId(request.getProductId())
                .batchId(request.getBatchId())
                .bottleCode(request.getBottleCode())
                .initialQuantity(request.getInitialQuantity())
                .currentQuantity(request.getInitialQuantity())
                .status(BottleStatus.AVAILABLE)
                .build();
    }

    public BottleResponse toResponse(Bottle bottle) {
        return BottleResponse.builder()
                .id(bottle.getId())
                .tenantId(bottle.getTenantId())
                .productId(bottle.getProductId())
                .batchId(bottle.getBatchId())
                .bottleCode(bottle.getBottleCode())
                .initialQuantity(bottle.getInitialQuantity())
                .currentQuantity(bottle.getCurrentQuantity())
                .status(bottle.getStatus())
                .createdAt(bottle.getCreatedAt())
                .updatedAt(bottle.getUpdatedAt())
                .build();
    }
}