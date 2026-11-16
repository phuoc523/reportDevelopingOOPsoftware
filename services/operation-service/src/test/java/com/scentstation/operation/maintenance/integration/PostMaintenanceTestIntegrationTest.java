package com.scentstation.operation.maintenance.integration;

import com.scentstation.operation.maintenance.dto.request.RequestPostMaintenanceTestRequest;
import com.scentstation.operation.maintenance.entity.MaintenanceRepairLog;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceRepairResult;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.integration.PostMaintenanceMachineClient;
import com.scentstation.operation.maintenance.repository.MaintenanceRepairLogRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import com.scentstation.operation.maintenance.repository.PostMaintenanceTestRepository;
import com.scentstation.operation.maintenance.service.PostMaintenanceTestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostMaintenanceTestIntegrationTest {

    @Autowired
    private PostMaintenanceTestService service;

    @Autowired
    private MaintenanceTicketRepository ticketRepository;

    @Autowired
    private MaintenanceRepairLogRepository repairLogRepository;

    @Autowired
    private PostMaintenanceTestRepository testRepository;

    @MockBean
    private PostMaintenanceMachineClient machineClient;

    private Long ticketId;

    @BeforeEach
    void setUp() {
        testRepository.deleteAll();
        repairLogRepository.deleteAll();
        ticketRepository.deleteAll();

        MaintenanceTicket ticket = ticketRepository.saveAndFlush(
                MaintenanceTicket.builder()
                        .tenantId(1L)
                        .machineId(99L)
                        .title("Repair pump")
                        .description("Pump failed")
                        .category("PUMP")
                        .severity(MaintenanceSeverity.MAJOR)
                        .priority(MaintenancePriority.HIGH)
                        .status(MaintenanceStatus.IN_PROGRESS)
                        .reportedByActorId(3L)
                        .build()
        );
        ticketId = ticket.getId();

        repairLogRepository.saveAndFlush(
                MaintenanceRepairLog.builder()
                        .tenantId(1L)
                        .ticketId(ticketId)
                        .diagnosedByActorId(5L)
                        .diagnosticResult("seal damaged")
                        .correctiveAction("replace seal")
                        .repairResult(MaintenanceRepairResult.SUCCESS)
                        .build()
        );
    }

    @Test
    void shouldRollbackRequestedTestWhenMachineClientFails() {
        when(machineClient.requestPostMaintenanceTest(1L, 99L, ticketId))
                .thenThrow(new IllegalStateException("Machine unavailable"));

        long before = testRepository.count();
        assertThrows(IllegalStateException.class, () ->
                service.requestTest(ticketId, request())
        );
        assertEquals(before, testRepository.count());
    }

    @Test
    void shouldPersistExternalTestIdWhenMachineAcceptsRequest() {
        when(machineClient.requestPostMaintenanceTest(1L, 99L, ticketId))
                .thenReturn("EXT-INTEGRATION");

        var response = service.requestTest(ticketId, request());

        assertEquals("EXT-INTEGRATION", response.getExternalTestId());
        assertEquals(1, testRepository.count());
        assertEquals(
                "EXT-INTEGRATION",
                testRepository.findByTenantIdAndExternalTestId(1L, "EXT-INTEGRATION")
                        .orElseThrow().getExternalTestId()
        );
    }

    private RequestPostMaintenanceTestRequest request() {
        RequestPostMaintenanceTestRequest request = new RequestPostMaintenanceTestRequest();
        request.setTenantId(1L);
        request.setRequestedByActorId(5L);
        return request;
    }
}
