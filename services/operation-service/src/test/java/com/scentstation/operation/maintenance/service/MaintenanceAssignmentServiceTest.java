package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.dto.request.AssignMaintenanceRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceAssignmentResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceAssignmentHistory;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenanceAssignmentEventType;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.exception.InvalidMaintenanceTicketException;
import com.scentstation.operation.maintenance.repository.MaintenanceAssignmentHistoryRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceAssignmentServiceTest {

    @Mock
    private MaintenanceTicketRepository maintenanceTicketRepository;

    @Mock
    private MaintenanceAssignmentHistoryRepository
            assignmentHistoryRepository;

    @Mock
    private MaintenanceAuditService maintenanceAuditService;

    @InjectMocks
    private MaintenanceAssignmentService maintenanceAssignmentService;

    private MaintenanceTicket ticket;

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
                .status(MaintenanceStatus.OPEN)
                .reportedByActorId(100L)
                .build();
    }

    @Test
    void shouldAssignTechnician() {
        AssignMaintenanceRequest request =
                new AssignMaintenanceRequest();

        request.setTenantId(10L);
        request.setTechnicianActorId(200L);
        request.setAssignedByActorId(100L);

        when(
                maintenanceTicketRepository
                        .findByIdAndTenantId(
                                1L,
                                10L
                        )
        ).thenReturn(Optional.of(ticket));

        when(
                maintenanceTicketRepository.save(ticket)
        ).thenReturn(ticket);

        when(
                assignmentHistoryRepository.save(any())
        ).thenAnswer(invocation -> {
            MaintenanceAssignmentHistory history =
                    invocation.getArgument(0);

            history.setId(1L);

            return history;
        });

        MaintenanceAssignmentResponse response =
                maintenanceAssignmentService.assignTechnician(
                        1L,
                        request
                );

        assertEquals(
                200L,
                ticket.getAssignedToActorId()
        );

        assertEquals(
                MaintenanceStatus.ASSIGNED,
                ticket.getStatus()
        );

        assertEquals(
                MaintenanceAssignmentEventType.ASSIGNED,
                response.getEventType()
        );
    }

    @Test
    void shouldReassignTechnician() {
        ticket.setAssignedToActorId(200L);
        ticket.setStatus(MaintenanceStatus.ASSIGNED);

        AssignMaintenanceRequest request =
                new AssignMaintenanceRequest();

        request.setTenantId(10L);
        request.setTechnicianActorId(300L);
        request.setAssignedByActorId(100L);

        when(
                maintenanceTicketRepository
                        .findByIdAndTenantId(
                                1L,
                                10L
                        )
        ).thenReturn(Optional.of(ticket));

        when(
                assignmentHistoryRepository.save(any())
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        MaintenanceAssignmentResponse response =
                maintenanceAssignmentService.assignTechnician(
                        1L,
                        request
                );

        assertEquals(
                300L,
                ticket.getAssignedToActorId()
        );

        assertEquals(
                200L,
                response.getPreviousTechnicianActorId()
        );

        assertEquals(
                MaintenanceAssignmentEventType.REASSIGNED,
                response.getEventType()
        );
    }

    @Test
    void shouldAcknowledgeAssignment() {
        ticket.setAssignedToActorId(200L);
        ticket.setStatus(MaintenanceStatus.ASSIGNED);

        when(
                maintenanceTicketRepository
                        .findByIdAndTenantId(
                                1L,
                                10L
                        )
        ).thenReturn(Optional.of(ticket));

        when(
                assignmentHistoryRepository.save(any())
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        MaintenanceAssignmentResponse response =
                maintenanceAssignmentService
                        .acknowledgeAssignment(
                                10L,
                                1L,
                                200L
                        );

        assertEquals(
                MaintenanceStatus.IN_PROGRESS,
                ticket.getStatus()
        );

        assertEquals(
                MaintenanceAssignmentEventType.ACKNOWLEDGED,
                response.getEventType()
        );
    }

    @Test
    void shouldRejectAcknowledgementFromWrongTechnician() {
        ticket.setAssignedToActorId(200L);
        ticket.setStatus(MaintenanceStatus.ASSIGNED);

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
                        maintenanceAssignmentService
                                .acknowledgeAssignment(
                                        10L,
                                        1L,
                                        999L
                                )
        );
    }

    @Test
    void shouldRejectAssignmentWhenTicketCompleted() {
        ticket.setStatus(
                MaintenanceStatus.COMPLETED
        );

        AssignMaintenanceRequest request =
                new AssignMaintenanceRequest();

        request.setTenantId(10L);
        request.setTechnicianActorId(200L);
        request.setAssignedByActorId(100L);

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
                        maintenanceAssignmentService
                                .assignTechnician(
                                        1L,
                                        request
                                )
        );
    }
}
