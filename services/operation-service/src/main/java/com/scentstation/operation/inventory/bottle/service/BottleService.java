package com.scentstation.operation.inventory.bottle.service;

import com.scentstation.operation.inventory.audit.entity.InventoryAuditEvent;
import com.scentstation.operation.inventory.audit.enums.InventoryAuditAction;
import com.scentstation.operation.inventory.audit.service.InventoryAuditService;
import com.scentstation.operation.inventory.batch.entity.InventoryBatch;
import com.scentstation.operation.inventory.batch.enums.InventoryBatchStatus;
import com.scentstation.operation.inventory.batch.exception.InventoryBatchExpiredException;
import com.scentstation.operation.inventory.batch.exception.InventoryBatchNotFoundException;
import com.scentstation.operation.inventory.batch.repository.InventoryBatchRepository;
import com.scentstation.operation.inventory.bottle.dto.request.CreateBottleRequest;
import com.scentstation.operation.inventory.bottle.dto.request.DisposeBottleRequest;
import com.scentstation.operation.inventory.bottle.dto.response.BottleResponse;
import com.scentstation.operation.inventory.bottle.entity.Bottle;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.bottle.exception.BottleAlreadyExistsException;
import com.scentstation.operation.inventory.bottle.exception.BottleBatchMismatchException;
import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import com.scentstation.operation.inventory.bottle.mapper.BottleMapper;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BottleService {

    private final BottleRepository bottleRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final BottleInstallationRepository installationRepository;
    private final BottleMovementRepository movementRepository;
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

    // Hủy / báo hư một chai nước hoa (lỗi, hư hỏng, trầy xước, hết hạn) kèm lý do và ghi movement + audit.
    @Transactional
    public BottleResponse disposeBottle(Long id, DisposeBottleRequest request) {
        Bottle bottle = bottleRepository
                .findByIdAndTenantId(id, request.getTenantId())
                .orElseThrow(() -> new BottleNotFoundException(id, request.getTenantId()));

        BottleStatus targetStatus = request.getTargetStatus() != null
                ? request.getTargetStatus()
                : BottleStatus.DAMAGED;
        if (targetStatus != BottleStatus.DAMAGED && targetStatus != BottleStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetStatus must be DAMAGED or EXPIRED");
        }
        if ((bottle.getStatus() == BottleStatus.DAMAGED || bottle.getStatus() == BottleStatus.EXPIRED)
                && bottle.getCurrentQuantity().compareTo(BigDecimal.ZERO) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chai nước hoa này đã được hủy trước đó.");
        }

        BigDecimal quantityBefore = bottle.getCurrentQuantity();
        BottleStatus statusBefore = bottle.getStatus();
        String trimmedReason = request.getReason().trim();

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

        bottle.setStatus(targetStatus);
        bottle.setCurrentQuantity(BigDecimal.ZERO);
        Bottle saved = bottleRepository.save(bottle);

        movementRepository.save(
                BottleMovement.builder()
                        .tenantId(request.getTenantId())
                        .bottleId(saved.getId())
                        .movementType(BottleMovementType.DISPOSE)
                        .machineId(machineId)
                        .slotId(slotId)
                        .quantityBefore(quantityBefore)
                        .quantityAfter(BigDecimal.ZERO)
                        .actorId(request.getActorId())
                        .reason(trimmedReason)
                        .build()
        );

        auditService.record(
                InventoryAuditEvent.builder()
                        .tenantId(request.getTenantId())
                        .actorId(request.getActorId())
                        .action(InventoryAuditAction.BOTTLE_DISPOSED)
                        .entityType("BOTTLE")
                        .entityId(saved.getId())
                        .machineId(machineId)
                        .slotId(slotId)
                        .beforeValue(statusBefore + ":" + quantityBefore)
                        .afterValue(targetStatus + ":0")
                        .reason(trimmedReason)
                        .build()
        );

        return bottleMapper.toResponse(saved);
    }

    // Không cho phép tạo chai từ một lô hàng đã hết hạn hoặc đã hủy.
    private void validateBatchNotExpired(
            InventoryBatch batch
    ) {
        if (batch.getStatus() == InventoryBatchStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể tạo chai từ lô hàng đã hủy.");
        }
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
