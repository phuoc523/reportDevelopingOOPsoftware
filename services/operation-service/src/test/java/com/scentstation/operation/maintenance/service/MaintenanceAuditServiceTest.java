package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.entity.MaintenanceAuditEvent;
import com.scentstation.operation.maintenance.enums.MaintenanceAuditAction;
import com.scentstation.operation.maintenance.repository.MaintenanceAuditEventRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaintenanceAuditServiceTest {

    @Test
    void shouldPersistAuditAndPropagateRepositoryFailure() {
        MaintenanceAuditEventRepository repository =
                mock(MaintenanceAuditEventRepository.class);
        MaintenanceAuditService service = new MaintenanceAuditService(repository);
        MaintenanceAuditEvent event = MaintenanceAuditEvent.builder()
                .tenantId(1L)
                .action(MaintenanceAuditAction.TICKET_CREATED)
                .entityType("TICKET")
                .source("MAINTENANCE")
                .build();

        when(repository.save(event)).thenReturn(event);
        assertSame(event, service.record(event));

        RuntimeException failure = new RuntimeException("audit unavailable");
        when(repository.save(event)).thenThrow(failure);
        assertSame(failure, assertThrows(
                RuntimeException.class,
                () -> service.record(event)
        ));
    }
}
