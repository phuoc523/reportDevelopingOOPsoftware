package com.scentstation.operation.inventory.audit.service;

import com.scentstation.operation.inventory.audit.entity.InventoryAuditEvent;
import com.scentstation.operation.inventory.audit.enums.InventoryAuditAction;
import com.scentstation.operation.inventory.audit.repository.InventoryAuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryAuditServiceTest {

    @Mock
    private InventoryAuditEventRepository repository;

    @InjectMocks
    private InventoryAuditService service;

    @Test
    void shouldQueryTenantActionAndEntityHistory() {
        InventoryAuditEvent event = InventoryAuditEvent.builder()
                .tenantId(1L)
                .action(InventoryAuditAction.INVENTORY_ADJUSTMENT)
                .entityType("BOTTLE")
                .entityId(10L)
                .build();

        when(repository.findAllByTenantIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(event));
        when(repository.findAllByTenantIdAndActionOrderByCreatedAtDesc(
                1L,
                InventoryAuditAction.INVENTORY_ADJUSTMENT
        )).thenReturn(List.of(event));
        when(repository
                .findAllByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        1L,
                        "BOTTLE",
                        10L
                )).thenReturn(List.of(event));

        assertEquals(1, service.getTenantHistory(1L).size());
        assertEquals(1, service.getHistoryByAction(
                1L,
                InventoryAuditAction.INVENTORY_ADJUSTMENT
        ).size());
        assertEquals(1, service.getEntityHistory(
                1L,
                "BOTTLE",
                10L
        ).size());
    }
}
