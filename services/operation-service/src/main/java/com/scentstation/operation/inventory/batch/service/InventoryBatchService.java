package com.scentstation.operation.inventory.batch.service;

import com.scentstation.operation.inventory.batch.dto.request.CreateInventoryBatchRequest;
import com.scentstation.operation.inventory.batch.dto.response.InventoryBatchResponse;
import com.scentstation.operation.inventory.batch.entity.InventoryBatch;
import com.scentstation.operation.inventory.batch.exception.InvalidInventoryBatchDateException;
import com.scentstation.operation.inventory.batch.exception.InventoryBatchAlreadyExistsException;
import com.scentstation.operation.inventory.batch.mapper.InventoryBatchMapper;
import com.scentstation.operation.inventory.batch.repository.InventoryBatchRepository;
import com.scentstation.operation.inventory.batch.exception.InventoryBatchNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryBatchService {

    private final InventoryBatchRepository inventoryBatchRepository;
    private final InventoryBatchMapper inventoryBatchMapper;

    // Tạo lô hàng mới sau khi kiểm tra mã lô và khoảng ngày hợp lệ.
    @Transactional
    public InventoryBatchResponse createBatch(
            CreateInventoryBatchRequest request
    ) {
        validateBatchCodeUniqueness(request);
        validateDates(request);

        InventoryBatch batch = inventoryBatchMapper.toEntity(request);

        InventoryBatch savedBatch =
                inventoryBatchRepository.save(batch);

        return inventoryBatchMapper.toResponse(savedBatch);
    }

    // Lấy toàn bộ lô hàng thuộc một tenant.
    @Transactional(readOnly = true)
    public List<InventoryBatchResponse> getBatchesByTenant(
            Long tenantId
    ) {
        return inventoryBatchRepository
                .findAllByTenantId(tenantId)
                .stream()
                .map(inventoryBatchMapper::toResponse)
                .toList();
    }

    // Lọc các lô hàng của tenant theo sản phẩm.
    @Transactional(readOnly = true)
    public List<InventoryBatchResponse> getBatchesByTenantAndProduct(
            Long tenantId,
            Long productId
    ) {
        return inventoryBatchRepository
                .findAllByTenantIdAndProductId(tenantId, productId)
                .stream()
                .map(inventoryBatchMapper::toResponse)
                .toList();
    }

    // Tìm một lô hàng theo tenant và id, báo lỗi nếu không tồn tại.
    @Transactional(readOnly = true)
    public InventoryBatchResponse getBatchById(Long tenantId, Long id) {
        InventoryBatch batch = inventoryBatchRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new InventoryBatchNotFoundException(tenantId, id));
        return inventoryBatchMapper.toResponse(batch);
    }
    // Bảo đảm mã lô không bị trùng trong cùng tenant.
    private void validateBatchCodeUniqueness(
            CreateInventoryBatchRequest request
    ) {
        boolean exists =
                inventoryBatchRepository.existsByTenantIdAndBatchCode(
                        request.getTenantId(),
                        request.getBatchCode()
                );

        if (exists) {
            throw new InventoryBatchAlreadyExistsException(
                    request.getTenantId(),
                    request.getBatchCode()
            );
        }
    }

    // Bảo đảm ngày hết hạn không đứng trước ngày nhận hàng.
    private void validateDates(
            CreateInventoryBatchRequest request
    ) {
        if (request.getExpiryDate() != null
                && request.getExpiryDate()
                .isBefore(request.getReceivedAt())) {

            throw new InvalidInventoryBatchDateException(
                    request.getReceivedAt(),
                    request.getExpiryDate()
            );
        }
    }
}
