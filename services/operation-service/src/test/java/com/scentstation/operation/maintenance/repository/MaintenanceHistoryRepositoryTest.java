package com.scentstation.operation.maintenance.repository;

import com.scentstation.operation.maintenance.entity.MaintenanceAlertLink;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class MaintenanceHistoryRepositoryTest {

    @Autowired
    private MaintenanceTicketRepository ticketRepository;

    @Autowired
    private MaintenanceAlertLinkRepository alertRepository;

    @Test
    void shouldOrderAndIsolateMachineHistory() {
        MaintenanceTicket first = ticketRepository.saveAndFlush(ticket(1L, 100L, "First"));
        MaintenanceTicket second = ticketRepository.saveAndFlush(ticket(1L, 100L, "Second"));
        ticketRepository.saveAndFlush(ticket(1L, 200L, "Other machine"));
        ticketRepository.saveAndFlush(ticket(2L, 100L, "Other tenant"));

        var result = ticketRepository
                .findAllByTenantIdAndMachineIdOrderByCreatedAtDescIdDesc(1L, 100L);

        assertEquals(2, result.size());
        assertEquals(second.getId(), result.get(0).getId());
        assertEquals(first.getId(), result.get(1).getId());
    }

    @Test
    void shouldFindAlertOnlyByTenantAndTicket() {
        alertRepository.saveAndFlush(MaintenanceAlertLink.builder()
                .tenantId(2L).ticketId(10L).machineId(100L)
                .sourceAlertId("OTHER-TENANT").alertType("PUMP")
                .occurredAt(LocalDateTime.now()).build());

        assertTrue(alertRepository
                .findFirstByTenantIdAndTicketIdOrderByIdAsc(1L, 10L)
                .isEmpty());
    }

    private MaintenanceTicket ticket(Long tenantId, Long machineId, String title) {
        return MaintenanceTicket.builder()
                .tenantId(tenantId).machineId(machineId)
                .title(title).description("Maintenance").category("TEST")
                .severity(MaintenanceSeverity.MODERATE)
                .priority(MaintenancePriority.MEDIUM)
                .status(MaintenanceStatus.OPEN)
                .reportedByActorId(9L)
                .build();
    }
}
