package com.scentstation.operation.inventory.bottle.service;

import com.scentstation.operation.inventory.audit.entity.InventoryAuditEvent;
import com.scentstation.operation.inventory.audit.enums.InventoryAuditAction;
import com.scentstation.operation.inventory.audit.service.InventoryAuditService;
import com.scentstation.operation.inventory.batch.entity.InventoryBatch;
import com.scentstation.operation.inventory.batch.exception.InventoryBatchExpiredException;
import com.scentstation.operation.inventory.batch.exception.InventoryBatchNotFoundException;
import com.scentstation.operation.inventory.batch.repository.InventoryBatchRepository;
import com.scentstation.operation.inventory.bottle.dto.request.CreateBottleRequest;
import com.scentstation.operation.inventory.bottle.dto.response.BottleResponse;
import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.exception.BottleAlreadyExistsException;
import com.scentstation.operation.inventory.bottle.exception.BottleBatchMismatchException;
import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import com.scentstation.operation.inventory.bottle.mapper.BottleMapper;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BottleService {

    private final BottleRepository bottleRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final BottleMapper bottleMapper;
    private final InventoryAuditService auditService;

    // Tạo chai mới sau khi kiểm tra mã chai, lô hàng, sản phẩm và hạn dùng.
    @Transactional
    public BottleResponse createBottle(
            CreateBottleRequest request
    ) {
        validateBottleCodeUniqueness(request);

        InventoryBatch batch = getValidBatch(request);

        validateProductMatchesBatch(
                request,
                batch
        );

        validateBatchNotExpired(batch);

        Bottle bottle = bottleMapper.toEntity(request);

        Bottle savedBottle = bottleRepository.save(bottle);

        auditService.record(
                InventoryAuditEvent.builder()
                        .tenantId(request.getTenantId())
                        .action(InventoryAuditAction.BOTTLE_CREATED)
                        .entityType("BOTTLE")
                        .entityId(savedBottle.getId())
                        .build()
        );

        return bottleMapper.toResponse(savedBottle);
    }

    // Tìm chai theo tenant và id, báo lỗi nếu không tồn tại.
    @Transactional(readOnly = true)
    public BottleResponse getBottleById(
            Long tenantId,
            Long id
    ) {
        Bottle bottle = bottleRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() ->
                        new BottleNotFoundException(
                                id,
                                tenantId
                        )
                );

        return bottleMapper.toResponse(bottle);
    }

    // Lấy toàn bộ chai thuộc một tenant.
    @Transactional(readOnly = true)
    public List<BottleResponse> getBottlesByTenant(
            Long tenantId
    ) {
        return bottleRepository
                .findAllByTenantId(tenantId)
                .stream()
                .map(bottleMapper::toResponse)
                .toList();
    }

    // Lọc chai của tenant theo lô hàng.
    @Transactional(readOnly = true)
    public List<BottleResponse> getBottlesByTenantAndBatch(
            Long tenantId,
            Long batchId
    ) {
        return bottleRepository
                .findAllByTenantIdAndBatchId(
                        tenantId,
                        batchId
                )
                .stream()
                .map(bottleMapper::toResponse)
                .toList();
    }

    // Lọc chai của tenant theo sản phẩm.
    @Transactional(readOnly = true)
    public List<BottleResponse> getBottlesByTenantAndProduct(
            Long tenantId,
            Long productId
    ) {
        return bottleRepository
                .findAllByTenantIdAndProductId(
                        tenantId,
                        productId
                )
                .stream()
                .map(bottleMapper::toResponse)
                .toList();
    }

    // Bảo đảm mã chai không bị trùng trong cùng tenant.
    private void validateBottleCodeUniqueness(
            CreateBottleRequest request
    ) {
        boolean exists =
                bottleRepository.existsByTenantIdAndBottleCode(
                        request.getTenantId(),
                        request.getBottleCode()
                );

        if (exists) {
            throw new BottleAlreadyExistsException(
                    request.getTenantId(),
                    request.getBottleCode()
            );
        }
    }

    // Lấy đúng lô hàng của tenant hoặc báo lỗi nếu không tìm thấy.
    private InventoryBatch getValidBatch(
            CreateBottleRequest request
    ) {
        return inventoryBatchRepository
                .findByIdAndTenantId(
                        request.getBatchId(),
                        request.getTenantId()
                )
                .orElseThrow(() ->
                        new InventoryBatchNotFoundException(
                                request.getBatchId(),
                                request.getTenantId()
                        )
                );
    }

    // Bảo đảm chai và lô hàng cùng tham chiếu một sản phẩm.
    private void validateProductMatchesBatch(
            CreateBottleRequest request,
            InventoryBatch batch
    ) {
        if (!batch.getProductId()
                .equals(request.getProductId())) {

            throw new BottleBatchMismatchException(
                    request.getProductId(),
                    batch.getProductId()
            );
        }
    }

    // Không cho phép tạo chai từ một lô hàng đã hết hạn.
    private void validateBatchNotExpired(
            InventoryBatch batch
    ) {
        if (batch.getExpiryDate() != null
                && batch.getExpiryDate()
                .isBefore(LocalDate.now())) {

            throw new InventoryBatchExpiredException(
                    batch.getId(),
                    batch.getExpiryDate()
            );
        }
    }
}
