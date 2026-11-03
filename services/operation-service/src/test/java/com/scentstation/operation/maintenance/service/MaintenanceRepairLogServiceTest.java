package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.dto.request.CreateMaintenanceRepairLogRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceRepairLogResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceRepairLog;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceRepairResult;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.exception.InvalidMaintenanceTicketException;
import com.scentstation.operation.maintenance.mapper.MaintenanceRepairLogMapper;
import com.scentstation.operation.maintenance.repository.MaintenanceRepairLogRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceRepairLogServiceTest {

    @Mock
    private MaintenanceRepairLogRepository maintenanceRepairLogRepository;

    @Mock
    private MaintenanceTicketRepository maintenanceTicketRepository;

    @Mock
    private MaintenanceRepairLogMapper maintenanceRepairLogMapper;

    @Mock
    private MaintenanceAuditService maintenanceAuditService;

    @InjectMocks
    private MaintenanceRepairLogService maintenanceRepairLogService;

    private MaintenanceTicket ticket;

    private MaintenanceRepairLog repairLog;

    @BeforeEach
    void setUp() {
        ticket = MaintenanceTicket.builder()
                .id(1L)
                .tenantId(10L)
                .machineId(20L)
                .title("Pump failure")
                .description("Pump failure")
                .category("PUMP_FAILURE")
                .severity(MaintenanceSeverity.MAJOR)
                .priority(MaintenancePriority.HIGH)
                .status(MaintenanceStatus.IN_PROGRESS)
                .reportedByActorId(100L)
                .build();

        repairLog = MaintenanceRepairLog.builder()
                .id(5L)
                .tenantId(10L)
                .ticketId(1L)
                .diagnosedByActorId(200L)
                .diagnosticResult("Pump pressure is low")
                .correctiveAction("Replace pump seal")
                .cost(new BigDecimal("150.00"))
                .repairResult(
                        MaintenanceRepairResult.SUCCESS
                )
                .build();
    }

    @Test
    void shouldRejectRepairResultFromWrongTechnician() {
        ticket.setAssignedToActorId(200L);

        CreateMaintenanceRepairLogRequest request =
                new CreateMaintenanceRepairLogRequest();

        request.setTenantId(10L);
        request.setDiagnosedByActorId(999L);
        request.setDiagnosticResult("Test");
        request.setCorrectiveAction("Test");
        request.setRepairResult(
                MaintenanceRepairResult.SUCCESS
        );

        when(
                maintenanceTicketRepository
                        .findByIdAndTenantId(
                                1L,
                                10L
                        )
        ).thenReturn(Optional.of(ticket));

        assertThrows(
                InvalidMaintenanceTicketException.class,
                () ->
                        maintenanceRepairLogService
                                .createRepairLog(
                                        1L,
                                        request
                                )
        );

        verify(
                maintenanceRepairLogRepository,
                never()
        ).save(any());
    }

    @Test
    void shouldCreateRepairLog() {
        CreateMaintenanceRepairLogRequest request =
                new CreateMaintenanceRepairLogRequest();

        request.setTenantId(10L);
        request.setDiagnosedByActorId(200L);
        request.setDiagnosticResult(
                "Pump pressure is low"
        );
        request.setCorrectiveAction(
                "Replace pump seal"
        );
        request.setCost(
                new BigDecimal("150.00")
        );
        request.setRepairResult(
                MaintenanceRepairResult.SUCCESS
        );

        MaintenanceRepairLogResponse response =
                MaintenanceRepairLogResponse.builder()
                        .id(5L)
                        .tenantId(10L)
                        .ticketId(1L)
                        .diagnosedByActorId(200L)
                        .repairResult(
                                MaintenanceRepairResult.SUCCESS
                        )
                        .build();

        when(
                maintenanceTicketRepository
                        .findByIdAndTenantId(
                                1L,
                                10L
                        )
        ).thenReturn(Optional.of(ticket));

        when(
                maintenanceRepairLogMapper
                        .toEntity(
                                1L,
                                request
                        )
        ).thenReturn(repairLog);

        when(
                maintenanceRepairLogRepository
                        .save(repairLog)
        ).thenReturn(repairLog);

        when(
                maintenanceRepairLogMapper
                        .toResponse(repairLog)
        ).thenReturn(response);

        MaintenanceRepairLogResponse result =
                maintenanceRepairLogService
                        .createRepairLog(
                                1L,
                                request
                        );

        assertEquals(5L, result.getId());

        assertEquals(
                MaintenanceRepairResult.SUCCESS,
                result.getRepairResult()
        );
    }

    @Test
    void shouldGetRepairLogs() {
        when(
                maintenanceTicketRepository
                        .findByIdAndTenantId(
                                1L,
                                10L
                        )
        ).thenReturn(Optional.of(ticket));

        when(
                maintenanceRepairLogRepository
                        .findAllByTenantIdAndTicketIdOrderByCreatedAtAsc(
                                10L,
                                1L
                        )
        ).thenReturn(List.of(repairLog));

        when(
                maintenanceRepairLogMapper
                        .toResponse(repairLog)
        ).thenReturn(
                MaintenanceRepairLogResponse.builder()
                        .id(5L)
                        .tenantId(10L)
                        .ticketId(1L)
                        .build()
        );

        List<MaintenanceRepairLogResponse> result =
                maintenanceRepairLogService
                        .getRepairLogs(
                                10L,
                                1L
                        );

        assertEquals(1, result.size());
    }

    @Test
    void shouldRejectRepairLogWhenTicketCompleted() {
        ticket.setStatus(
                MaintenanceStatus.COMPLETED
        );

        CreateMaintenanceRepairLogRequest request =
                new CreateMaintenanceRepairLogRequest();

        request.setTenantId(10L);
        request.setDiagnosedByActorId(200L);
        request.setDiagnosticResult("Test");
        request.setCorrectiveAction("Test");
        request.setRepairResult(
                MaintenanceRepairResult.SUCCESS
        );

        when(
                maintenanceTicketRepository
                        .findByIdAndTenantId(
                                1L,
                                10L
                        )
        ).thenReturn(Optional.of(ticket));

        assertThrows(
                InvalidMaintenanceTicketException.class,
                () ->
                        maintenanceRepairLogService
                                .createRepairLog(
                                        1L,
                                        request
                                )
        );

        verify(
                maintenanceRepairLogRepository,
                never()
        ).save(any());
    }
}
