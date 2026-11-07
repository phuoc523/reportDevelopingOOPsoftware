package com.scentstation.operation.maintenance.integration;

import com.scentstation.operation.maintenance.entity.MaintenanceAlertLink;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.repository.MaintenanceAlertLinkRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.doReturn;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CriticalMaintenanceAlertIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MaintenanceTicketRepository maintenanceTicketRepository;

    @Autowired
    private MaintenanceAlertLinkRepository maintenanceAlertLinkRepository;

    @SpyBean
    private MaintenanceAlertLinkRepository maintenanceAlertLinkRepositorySpy;

    @BeforeEach
    void setUp() {
        maintenanceAlertLinkRepository.deleteAll();
        maintenanceTicketRepository.deleteAll();
    }

    @Test
    void shouldCreateCriticalTicketFromAlert() throws Exception {
        String body = """
                {
                  "tenantId": 1,
                  "machineId": 10,
                  "locationId": 20,
                  "slotId": 30,
                  "alertId": "ALERT-001",
                  "alertType": "PUMP_FAILURE",
                  "message": "Critical pump pressure",
                  "occurredAt": "2026-11-03T10:00:00"
                }
                """;

        mockMvc.perform(
                        post("/api/maintenance/alerts/critical")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severity").value("CRITICAL"))
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        List<MaintenanceTicket> tickets =
                maintenanceTicketRepository
                        .findAllByTenantId(1L);

        assertEquals(1, tickets.size());

        MaintenanceTicket ticket = tickets.get(0);

        assertTrue(ticket.isCritical());
        assertEquals(1L, ticket.getTenantId());
        assertEquals(10L, ticket.getMachineId());
        assertEquals(20L, ticket.getLocationId());
        assertEquals(30L, ticket.getSlotId());
        assertEquals("PUMP_FAILURE", ticket.getCategory());
        assertEquals("Critical pump pressure", ticket.getDescription());
        assertEquals(MaintenanceSeverity.CRITICAL, ticket.getSeverity());
        assertEquals(MaintenancePriority.URGENT, ticket.getPriority());
        assertEquals(MaintenanceStatus.OPEN, ticket.getStatus());

        MaintenanceAlertLink link =
                maintenanceAlertLinkRepository
                        .findByTenantIdAndSourceAlertId(
                                1L,
                                "ALERT-001"
                        )
                        .orElseThrow();

        assertEquals(
                ticket.getId(),
                link.getTicketId()
        );
        assertEquals(1L, link.getTenantId());
        assertEquals("ALERT-001", link.getSourceAlertId());
        assertEquals("PUMP_FAILURE", link.getAlertType());
        assertEquals(10L, link.getMachineId());
        assertEquals(
                java.time.LocalDateTime.parse("2026-11-03T10:00:00"),
                link.getOccurredAt()
        );
    }

    @Test
    void shouldAllowSameAlertIdInDifferentTenant()
            throws Exception {

        String tenantOneBody = """
                {
                "tenantId": 1,
                "machineId": 10,
                "alertId": "ALERT-SHARED",
                "alertType": "PUMP_FAILURE",
                "message": "Tenant 1 alert",
                "occurredAt": "2026-11-03T10:00:00"
                }
                """;

        String tenantTwoBody = """
                {
                "tenantId": 2,
                "machineId": 20,
                "alertId": "ALERT-SHARED",
                "alertType": "PUMP_FAILURE",
                "message": "Tenant 2 alert",
                "occurredAt": "2026-11-03T10:01:00"
                }
                """;

        mockMvc.perform(
                        post("/api/maintenance/alerts/critical")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(tenantOneBody)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/maintenance/alerts/critical")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(tenantTwoBody)
                )
                .andExpect(status().isCreated());

        assertEquals(
                1,
                maintenanceTicketRepository
                        .findAllByTenantId(1L)
                        .size()
        );

        assertEquals(
                1,
                maintenanceTicketRepository
                        .findAllByTenantId(2L)
                        .size()
        );
    }

    @Test
    void shouldRejectDuplicateAlertWithoutCreatingSecondTicket()
            throws Exception {

        String body = """
                {
                  "tenantId": 1,
                  "machineId": 10,
                  "alertId": "ALERT-001",
                  "alertType": "PUMP_FAILURE",
                  "message": "Critical pump pressure",
                  "occurredAt": "2026-11-03T10:00:00"
                }
                """;

        mockMvc.perform(
                        post("/api/maintenance/alerts/critical")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/maintenance/alerts/critical")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isConflict());

        assertEquals(
                1,
                maintenanceTicketRepository
                        .findAllByTenantId(1L)
                        .size()
        );

        assertEquals(1, maintenanceAlertLinkRepository.count());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRollbackTicketWhenConcurrentAlertLinkInsertWins()
            throws Exception {
        maintenanceAlertLinkRepository.saveAndFlush(
                MaintenanceAlertLink.builder()
                        .tenantId(1L)
                        .sourceAlertId("ALERT-RACE")
                        .alertType("PUMP_FAILURE")
                        .ticketId(999L)
                        .machineId(10L)
                        .occurredAt(java.time.LocalDateTime.parse("2026-11-03T10:00:00"))
                        .build()
        );

        doReturn(false)
                .when(maintenanceAlertLinkRepositorySpy)
                .existsByTenantIdAndSourceAlertId(1L, "ALERT-RACE");

        String body = """
                {
                  "tenantId": 1,
                  "machineId": 10,
                  "alertId": "ALERT-RACE",
                  "alertType": "PUMP_FAILURE",
                  "message": "Concurrent critical alert",
                  "occurredAt": "2026-11-03T10:00:00"
                }
                """;

        mockMvc.perform(
                        post("/api/maintenance/alerts/critical")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isConflict());

        assertEquals(0, maintenanceTicketRepository.count());
        assertEquals(1, maintenanceAlertLinkRepository.count());
    }
}
