package com.scentstation.operation.inventory.audit.service;

import com.scentstation.operation.inventory.audit.dto.response.InventoryAuditEventResponse;
import com.scentstation.operation.inventory.audit.entity.InventoryAuditEvent;
import com.scentstation.operation.inventory.audit.enums.InventoryAuditAction;
import com.scentstation.operation.inventory.audit.repository.InventoryAuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryAuditService {

    private final InventoryAuditEventRepository auditEventRepository;

    // Lưu audit event trong transaction của nghiệp vụ gọi phương thức này.
    public InventoryAuditEvent record(InventoryAuditEvent event) {
        return auditEventRepository.save(event);
    }

    // Trả lịch sử audit mới nhất trước của một tenant.
    @Transactional(readOnly = true)
    public List<InventoryAuditEventResponse> getTenantHistory(Long tenantId) {
        return auditEventRepository
                .findAllByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Lọc lịch sử audit của tenant theo action nghiệp vụ.
    @Transactional(readOnly = true)
    public List<InventoryAuditEventResponse> getHistoryByAction(
            Long tenantId,
            InventoryAuditAction action
    ) {
        return auditEventRepository
                .findAllByTenantIdAndActionOrderByCreatedAtDesc(
                        tenantId,
                        action
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Trả lịch sử audit của một entity trong phạm vi tenant.
    @Transactional(readOnly = true)
    public List<InventoryAuditEventResponse> getEntityHistory(
            Long tenantId,
            String entityType,
            Long entityId
    ) {
        return auditEventRepository
                .findAllByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        tenantId,
                        entityType,
                        entityId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Chuyển entity audit thành response chỉ đọc cho API.
    private InventoryAuditEventResponse toResponse(
            InventoryAuditEvent event
    ) {
        return InventoryAuditEventResponse.builder()
                .id(event.getId())
                .tenantId(event.getTenantId())
                .actorId(event.getActorId())
                .action(event.getAction())
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .machineId(event.getMachineId())
                .slotId(event.getSlotId())
                .beforeValue(event.getBeforeValue())
                .afterValue(event.getAfterValue())
                .reason(event.getReason())
                .referenceId(event.getReferenceId())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
