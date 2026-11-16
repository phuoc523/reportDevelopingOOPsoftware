package com.scentstation.operation.maintenance.repository;

import com.scentstation.operation.maintenance.entity.MaintenanceAuditEvent;
import com.scentstation.operation.maintenance.enums.MaintenanceAuditAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class MaintenanceAuditEventRepositoryTest {

    @Autowired
    private MaintenanceAuditEventRepository repository;

    @Test
    void shouldPersistImmutableTenantScopedEvent() {
        MaintenanceAuditEvent event = repository.saveAndFlush(
                MaintenanceAuditEvent.builder()
                        .tenantId(10L)
                        .action(MaintenanceAuditAction.TICKET_CREATED)
                        .entityType("TICKET")
                        .entityId(7L)
                        .ticketId(7L)
                        .machineId(9L)
                        .source("MAINTENANCE")
                        .build()
        );

        assertNotNull(event.getId());
        assertNotNull(event.getCreatedAt());
        assertEquals(1, repository
                .findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(10L, 7L)
                .size());
        assertTrue(repository
                .findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(11L, 7L)
                .isEmpty());
        assertTrue(Arrays.stream(MaintenanceAuditEvent.class.getMethods())
                .map(Method::getName)
                .noneMatch(name -> name.startsWith("set")));
    }
}
