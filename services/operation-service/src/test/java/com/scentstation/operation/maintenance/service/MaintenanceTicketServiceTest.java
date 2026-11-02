package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.dto.request.CreateMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.request.UpdateMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceTicketResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.mapper.MaintenanceTicketMapper;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.scentstation.operation.maintenance.exception.MaintenanceNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceTicketServiceTest {

    @Mock
    private MaintenanceTicketRepository maintenanceTicketRepository;

    @Mock
    private MaintenanceTicketMapper maintenanceTicketMapper;

    @InjectMocks
    private MaintenanceTicketService maintenanceTicketService;

    private MaintenanceTicket ticket;

    private MaintenanceTicketResponse response;

    @BeforeEach
    void setUp() {
        ticket = MaintenanceTicket.builder()
                .id(1L)
                .tenantId(10L)
                .machineId(20L)
                .title("Pump error")
                .description("Machine does not spray correctly")
                .category("PUMP_FAILURE")
                .severity(MaintenanceSeverity.MAJOR)
                .priority(MaintenancePriority.HIGH)
                .status(MaintenanceStatus.OPEN)
                .reportedByActorId(100L)
                .build();

        response = MaintenanceTicketResponse.builder()
                .id(1L)
                .tenantId(10L)
                .machineId(20L)
                .title("Pump error")
                .status(MaintenanceStatus.OPEN)
                .build();
    }

        @Test
        void shouldUpdateBasicTicketInformation() {
        UpdateMaintenanceTicketRequest request =
                new UpdateMaintenanceTicketRequest();

        request.setTitle("Updated");
        request.setDescription("Updated description");
        request.setCategory("PUMP_FAILURE");
        request.setSeverity(
                MaintenanceSeverity.CRITICAL
        );
        request.setPriority(
                MaintenancePriority.URGENT
        );

        when(
                maintenanceTicketRepository
                        .findByIdAndTenantId(
                                1L,
                                10L
                        )
        ).thenReturn(Optional.of(ticket));

        when(
                maintenanceTicketRepository
                        .save(ticket)
        ).thenReturn(ticket);

        when(
                maintenanceTicketMapper
                        .toResponse(ticket)
        ).thenReturn(response);

        maintenanceTicketService.updateTicket(
                10L,
                1L,
                request
        );

        assertEquals(
                "Updated",
                ticket.getTitle()
        );

        assertEquals(
                MaintenanceSeverity.CRITICAL,
                ticket.getSeverity()
        );

        assertEquals(
                MaintenancePriority.URGENT,
                ticket.getPriority()
        );

        verify(
                maintenanceTicketRepository
        ).save(ticket);
        }

        @Test
        void shouldReturnTicketsByStatus() {
        when(
                maintenanceTicketRepository
                        .findAllByTenantIdAndStatus(
                                10L,
                                MaintenanceStatus.OPEN
                        )
        ).thenReturn(List.of(ticket));

        when(
                maintenanceTicketMapper
                        .toResponse(ticket)
        ).thenReturn(response);

        List<MaintenanceTicketResponse> result =
                maintenanceTicketService
                        .getTicketsByStatus(
                                10L,
                                MaintenanceStatus.OPEN
                        );

        assertEquals(1, result.size());

        verify(
                maintenanceTicketRepository
        ).findAllByTenantIdAndStatus(
                10L,
                MaintenanceStatus.OPEN
        );
        }

    @Test
    void shouldCreateMaintenanceTicket() {
        CreateMaintenanceTicketRequest request =
                new CreateMaintenanceTicketRequest();

        request.setTenantId(10L);
        request.setMachineId(20L);
        request.setTitle("Pump error");
        request.setDescription(
                "Machine does not spray correctly"
        );
        request.setCategory("PUMP_FAILURE");
        request.setSeverity(MaintenanceSeverity.MAJOR);
        request.setPriority(MaintenancePriority.HIGH);
        request.setReportedByActorId(100L);

        when(maintenanceTicketMapper.toEntity(request))
                .thenReturn(ticket);

        when(maintenanceTicketRepository.save(ticket))
                .thenReturn(ticket);

        when(maintenanceTicketMapper.toResponse(ticket))
                .thenReturn(response);

        MaintenanceTicketResponse result =
                maintenanceTicketService
                        .createTicket(request);

        assertEquals(1L, result.getId());
        assertEquals(
                MaintenanceStatus.OPEN,
                result.getStatus()
        );

        verify(maintenanceTicketRepository)
                .save(ticket);
    }

    @Test
    void shouldGetTicketInsideTenant() {
        when(
                maintenanceTicketRepository
                        .findByIdAndTenantId(
                                1L,
                                10L
                        )
        ).thenReturn(Optional.of(ticket));

        when(maintenanceTicketMapper.toResponse(ticket))
                .thenReturn(response);

        MaintenanceTicketResponse result =
                maintenanceTicketService
                        .getTicketById(
                                10L,
                                1L
                        );

        assertEquals(1L, result.getId());
    }

    @Test
    void shouldListTicketsByTenant() {
        when(
                maintenanceTicketRepository
                        .findAllByTenantId(10L)
        ).thenReturn(List.of(ticket));

        when(maintenanceTicketMapper.toResponse(ticket))
                .thenReturn(response);

        List<MaintenanceTicketResponse> results =
                maintenanceTicketService
                        .getTickets(10L);

        assertEquals(1, results.size());
    }
}