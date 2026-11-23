package com.scentstation.operation.inventory.batch.service;

import com.scentstation.operation.inventory.audit.entity.InventoryAuditEvent;
import com.scentstation.operation.inventory.audit.enums.InventoryAuditAction;
import com.scentstation.operation.inventory.audit.service.InventoryAuditService;
import com.scentstation.operation.inventory.batch.dto.request.CreateInventoryBatchRequest;
import com.scentstation.operation.inventory.batch.dto.request.DisposeInventoryBatchRequest;
import com.scentstation.operation.inventory.batch.dto.response.InventoryBatchResponse;
import com.scentstation.operation.inventory.batch.entity.InventoryBatch;
import com.scentstation.operation.inventory.batch.enums.InventoryBatchStatus;
import com.scentstation.operation.inventory.batch.exception.InvalidInventoryBatchDateException;
import com.scentstation.operation.inventory.batch.exception.InventoryBatchAlreadyExistsException;
import com.scentstation.operation.inventory.batch.mapper.InventoryBatchMapper;
import com.scentstation.operation.inventory.batch.repository.InventoryBatchRepository;
import com.scentstation.operation.inventory.batch.exception.InventoryBatchNotFoundException;
import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.installation.entity.BottleInstallation;
import com.scentstation.operation.inventory.installation.repository.BottleInstallationRepository;
import com.scentstation.operation.inventory.movement.entity.BottleMovement;
import com.scentstation.operation.inventory.movement.enums.BottleMovementType;
import com.scentstation.operation.inventory.movement.repository.BottleMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InventoryBatchService {

    private final InventoryBatchRepository inventoryBatchRepository;
    private final BottleRepository bottleRepository;
    private final BottleInstallationRepository installationRepository;
    private final BottleMovementRepository movementRepository;
    private final InventoryBatchMapper inventoryBatchMapper;
    private final InventoryAuditService auditService;

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

        auditService.record(
                InventoryAuditEvent.builder()
                        .tenantId(request.getTenantId())
                        .action(InventoryAuditAction.BATCH_CREATED)
                        .entityType("BATCH")
                        .entityId(savedBatch.getId())
                        .build()
        );

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

    // Hủy lô hàng bị lỗi và tự động hủy các chai chưa bị hủy thuộc lô đó.
    @Transactional
    public InventoryBatchResponse disposeBatch(Long id, DisposeInventoryBatchRequest request) {
        InventoryBatch batch = inventoryBatchRepository
                .findByIdAndTenantId(id, request.getTenantId())
                .orElseThrow(() -> new InventoryBatchNotFoundException(request.getTenantId(), id));

        if (batch.getStatus() == InventoryBatchStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lô hàng này đã được hủy trước đó.");
        }

        String trimmedReason = request.getReason().trim();
        batch.setStatus(InventoryBatchStatus.CANCELLED);
        batch.setCancelReason(trimmedReason);
        batch.setCancelledAt(LocalDateTime.now());
        InventoryBatch savedBatch = inventoryBatchRepository.save(batch);

        List<Bottle> bottles = bottleRepository.findAllByTenantIdAndBatchId(request.getTenantId(), id);
        for (Bottle bottle : bottles) {
            if ((bottle.getStatus() == BottleStatus.DAMAGED || bottle.getStatus() == BottleStatus.EXPIRED)
                    && bottle.getCurrentQuantity().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BigDecimal quantityBefore = bottle.getCurrentQuantity();
            Optional<BottleInstallation> activeInstallation = installationRepository
                    .findByTenantIdAndBottleIdAndActiveTrue(request.getTenantId(), bottle.getId());
            Long machineId = null;
            Long slotId = null;
            if (activeInstallation.isPresent()) {
                BottleInstallation installation = activeInstallation.get();
                machineId = installation.getMachineId();
                slotId = installation.getSlotId();
                installation.setActive(null);
                installation.setRemovedBy(request.getActorId());
                installation.setRemovedAt(LocalDateTime.now());
                installation.setQuantityAfterRemoval(BigDecimal.ZERO);
                installationRepository.save(installation);
            }
            bottle.setStatus(BottleStatus.DAMAGED);
            bottle.setCurrentQuantity(BigDecimal.ZERO);
            bottleRepository.save(bottle);

            movementRepository.save(
                    BottleMovement.builder()
                            .tenantId(request.getTenantId())
                            .bottleId(bottle.getId())
                            .movementType(BottleMovementType.DISPOSE)
                            .machineId(machineId)
                            .slotId(slotId)
                            .quantityBefore(quantityBefore)
                            .quantityAfter(BigDecimal.ZERO)
                            .actorId(request.getActorId())
                            .reason("Hủy lô " + batch.getBatchCode() + ": " + trimmedReason)
                            .build()
            );
        }

        auditService.record(
                InventoryAuditEvent.builder()
                        .tenantId(request.getTenantId())
                        .actorId(request.getActorId())
                        .action(InventoryAuditAction.BATCH_CANCELLED)
                        .entityType("BATCH")
                        .entityId(savedBatch.getId())
                        .beforeValue("ACTIVE")
                        .afterValue("CANCELLED")
                        .reason(trimmedReason)
                        .build()
        );

        return inventoryBatchMapper.toResponse(savedBatch);
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
