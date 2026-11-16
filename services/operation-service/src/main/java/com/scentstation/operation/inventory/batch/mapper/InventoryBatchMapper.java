package com.scentstation.operation.inventory.batch.mapper;

import com.scentstation.operation.inventory.batch.dto.request.CreateInventoryBatchRequest;
import com.scentstation.operation.inventory.batch.dto.response.InventoryBatchResponse;
import com.scentstation.operation.inventory.batch.entity.InventoryBatch;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class InventoryBatchMapper {

    // Chuyển create request thành inventory batch entity.
    public InventoryBatch toEntity(CreateInventoryBatchRequest request) {
        return InventoryBatch.builder()
                .tenantId(request.getTenantId())
                .productId(request.getProductId())
                .batchCode(request.getBatchCode())
                .receivedQuantity(request.getReceivedQuantity())
                .receivedAt(request.getReceivedAt())
                .expiryDate(request.getExpiryDate())
                .build();
    }

    // Chuyển batch entity thành response kèm trạng thái hết hạn.
    public InventoryBatchResponse toResponse(InventoryBatch batch) {
        return InventoryBatchResponse.builder()
                .id(batch.getId())
                .tenantId(batch.getTenantId())
                .productId(batch.getProductId())
                .batchCode(batch.getBatchCode())
                .receivedQuantity(batch.getReceivedQuantity())
                .receivedAt(batch.getReceivedAt())
                .expiryDate(batch.getExpiryDate())
                .expired(isExpired(batch))
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .build();
    }

    // Xác định batch đã quá ngày hết hạn hay chưa.
    private boolean isExpired(InventoryBatch batch) {
        return batch.getExpiryDate() != null
                && batch.getExpiryDate().isBefore(LocalDate.now());
    }
}
