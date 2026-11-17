package com.scentstation.operation.maintenance.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scentstation.operation.maintenance.dto.request.CreateChecklistItemRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceChecklistItemResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceChecklistItem;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.mapper.MaintenanceChecklistMapper;
import com.scentstation.operation.maintenance.repository.MaintenanceChecklistRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MaintenanceChecklistInvalidationTest {
    @Test
    void checklistCreateEvictsItsTenantAndTicketKeyAfterCommit() {
        MaintenanceChecklistRepository checklistRepository = mock(MaintenanceChecklistRepository.class);
        MaintenanceTicketRepository ticketRepository = mock(MaintenanceTicketRepository.class);
        MaintenanceChecklistMapper mapper = mock(MaintenanceChecklistMapper.class);
        MaintenanceAuditService audit = mock(MaintenanceAuditService.class);
        MaintenanceChecklistCache cache = mock(MaintenanceChecklistCache.class);
        MaintenanceTicket ticket = MaintenanceTicket.builder()
                .id(1L)
                .tenantId(17L)
                .machineId(4L)
                .title("Maintenance")
                .description("Maintenance")
                .category("TEST")
                .severity(MaintenanceSeverity.MODERATE)
                .priority(MaintenancePriority.MEDIUM)
                .status(MaintenanceStatus.IN_PROGRESS)
                .reportedByActorId(8L)
                .build();
        CreateChecklistItemRequest request = new CreateChecklistItemRequest();
        request.setTenantId(17L);
        request.setItemName("Inspect pump");
        request.setRequired(true);
        MaintenanceChecklistItem item = MaintenanceChecklistItem.builder()
                .id(5L)
                .tenantId(17L)
                .ticketId(1L)
                .itemName("Inspect pump")
                .required(true)
                .completed(false)
                .build();
        when(ticketRepository.findByIdAndTenantId(1L, 17L)).thenReturn(java.util.Optional.of(ticket));
        when(mapper.toEntity(1L, request)).thenReturn(item);
        when(checklistRepository.save(item)).thenReturn(item);
        when(mapper.toResponse(item)).thenReturn(MaintenanceChecklistItemResponse.builder()
                .id(5L)
                .tenantId(17L)
                .ticketId(1L)
                .itemName("Inspect pump")
                .required(true)
                .completed(false)
                .build());

        MaintenanceChecklistService service = new MaintenanceChecklistService(
                checklistRepository, ticketRepository, mapper, audit);
        service.setChecklistCache(cache);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.createChecklistItem(1L, request);

            verify(cache, never()).evict(17L, 1L);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(cache).evict(17L, 1L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
