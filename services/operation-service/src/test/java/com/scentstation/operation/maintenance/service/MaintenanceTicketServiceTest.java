package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.dto.request.CloseMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.request.CreateMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.request.ReopenMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.request.UpdateMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceTicketResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.entity.PostMaintenanceTest;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.enums.PostMaintenanceTestStatus;
import com.scentstation.operation.maintenance.exception.InvalidMaintenanceTicketException;
import com.scentstation.operation.maintenance.mapper.MaintenanceTicketMapper;
import com.scentstation.operation.maintenance.repository.MaintenanceChecklistRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceRepairLogRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import com.scentstation.operation.maintenance.repository.PostMaintenanceTestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
    private MaintenanceChecklistRepository maintenanceChecklistRepository;

    @Mock
    private MaintenanceRepairLogRepository maintenanceRepairLogRepository;

    @Mock
    private PostMaintenanceTestRepository postMaintenanceTestRepository;

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

    @Test
    void shouldCloseEligibleTicket() {
        stubEligibleClose();
        when(maintenanceTicketRepository.save(ticket)).thenReturn(ticket);
        when(maintenanceTicketMapper.toResponse(ticket)).thenAnswer(invocation ->
                MaintenanceTicketResponse.builder()
                        .status(ticket.getStatus())
                        .closedAt(ticket.getClosedAt())
                        .build()
        );

        MaintenanceTicketResponse result = maintenanceTicketService
                .closeTicket(1L, closeRequest());

        assertEquals(MaintenanceStatus.COMPLETED, result.getStatus());
        assertNotNull(result.getClosedAt());
    }

    @Test
    void shouldRejectCloseWithIncompleteRequiredChecklist() {
        stubTicketLookup();
        when(maintenanceChecklistRepository
                .existsByTenantIdAndTicketIdAndRequiredTrueAndCompletedFalse(10L, 1L))
                .thenReturn(true);
        assertThrows(
                InvalidMaintenanceTicketException.class,
                () -> maintenanceTicketService.closeTicket(1L, closeRequest())
        );
    }

    @Test
    void shouldAllowOptionalIncompleteChecklist() {
        stubEligibleClose();
        when(maintenanceTicketRepository.save(ticket)).thenReturn(ticket);
        when(maintenanceTicketMapper.toResponse(ticket)).thenReturn(response);
        assertDoesNotThrow(() ->
                maintenanceTicketService.closeTicket(1L, closeRequest())
        );
    }

    @Test
    void shouldRejectCloseWithoutRepairResult() {
        stubTicketLookup();
        assertThrows(
                InvalidMaintenanceTicketException.class,
                () -> maintenanceTicketService.closeTicket(1L, closeRequest())
        );
    }

    @Test
    void shouldRejectCloseWithoutPassedTest() {
        stubTicketLookup();
        when(maintenanceRepairLogRepository.existsByTenantIdAndTicketId(10L, 1L))
                .thenReturn(true);
        assertThrows(
                InvalidMaintenanceTicketException.class,
                () -> maintenanceTicketService.closeTicket(1L, closeRequest())
        );
    }

    @Test
    void shouldRejectCloseWhenLatestTestFailedAfterPassed() {
        stubTicketLookup();
        when(maintenanceRepairLogRepository.existsByTenantIdAndTicketId(10L, 1L))
                .thenReturn(true);
        when(postMaintenanceTestRepository.existsByTenantIdAndTicketIdAndStatus(
                10L, 1L, PostMaintenanceTestStatus.PASSED
        )).thenReturn(true);
        when(postMaintenanceTestRepository
                .findFirstByTenantIdAndTicketIdOrderByRequestedAtDescIdDesc(10L, 1L))
                .thenReturn(Optional.of(postTest(PostMaintenanceTestStatus.FAILED)));

        assertThrows(
                InvalidMaintenanceTicketException.class,
                () -> maintenanceTicketService.closeTicket(1L, closeRequest())
        );
    }

    @ParameterizedTest
    @EnumSource(value = MaintenanceStatus.class, names = {"COMPLETED", "CANCELLED"})
    void shouldRejectCloseForFinalTicket(MaintenanceStatus status) {
        ticket.setStatus(status);
        stubTicketLookup();
        assertThrows(
                InvalidMaintenanceTicketException.class,
                () -> maintenanceTicketService.closeTicket(1L, closeRequest())
        );
        verifyNoInteractions(maintenanceChecklistRepository);
    }

    @Test
    void shouldReopenCompletedAssignedTicketToInProgress() {
        ticket.setStatus(MaintenanceStatus.COMPLETED);
        ticket.setAssignedToActorId(200L);
        ticket.setClosedAt(java.time.LocalDateTime.now());
        stubTicketLookup();
        when(maintenanceTicketRepository.save(ticket)).thenReturn(ticket);
        when(maintenanceTicketMapper.toResponse(ticket)).thenAnswer(invocation ->
                MaintenanceTicketResponse.builder()
                        .status(ticket.getStatus())
                        .closedAt(ticket.getClosedAt())
                        .build()
        );

        MaintenanceTicketResponse result = maintenanceTicketService
                .reopenTicket(1L, reopenRequest());

        assertEquals(MaintenanceStatus.IN_PROGRESS, result.getStatus());
        assertNull(result.getClosedAt());
    }

    @Test
    void shouldReopenCompletedUnassignedTicketToOpen() {
        ticket.setStatus(MaintenanceStatus.COMPLETED);
        stubTicketLookup();
        when(maintenanceTicketRepository.save(ticket)).thenReturn(ticket);
        when(maintenanceTicketMapper.toResponse(ticket)).thenReturn(response);
        maintenanceTicketService.reopenTicket(1L, reopenRequest());
        assertEquals(MaintenanceStatus.OPEN, ticket.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = MaintenanceStatus.class, names = {
            "OPEN", "ASSIGNED", "IN_PROGRESS", "WAITING_FOR_PARTS",
            "READY_FOR_TEST", "CANCELLED"
    })
    void shouldRejectReopenUnlessCompleted(MaintenanceStatus status) {
        ticket.setStatus(status);
        stubTicketLookup();
        assertThrows(
                InvalidMaintenanceTicketException.class,
                () -> maintenanceTicketService.reopenTicket(1L, reopenRequest())
        );
    }

    @Test
    void shouldRejectCrossTenantCloseAndReopenAsNotFound() {
        when(maintenanceTicketRepository.findByIdAndTenantId(1L, 99L))
                .thenReturn(Optional.empty());
        CloseMaintenanceTicketRequest close = closeRequest();
        close.setTenantId(99L);
        ReopenMaintenanceTicketRequest reopen = reopenRequest();
        reopen.setTenantId(99L);

        assertThrows(
                MaintenanceNotFoundException.class,
                () -> maintenanceTicketService.closeTicket(1L, close)
        );
        assertThrows(
                MaintenanceNotFoundException.class,
                () -> maintenanceTicketService.reopenTicket(1L, reopen)
        );
    }

    private void stubEligibleClose() {
        stubTicketLookup();
        when(maintenanceRepairLogRepository.existsByTenantIdAndTicketId(10L, 1L))
                .thenReturn(true);
        when(postMaintenanceTestRepository.existsByTenantIdAndTicketIdAndStatus(
                10L, 1L, PostMaintenanceTestStatus.PASSED
        )).thenReturn(true);
        when(postMaintenanceTestRepository
                .findFirstByTenantIdAndTicketIdOrderByRequestedAtDescIdDesc(10L, 1L))
                .thenReturn(Optional.of(postTest(PostMaintenanceTestStatus.PASSED)));
    }

    private void stubTicketLookup() {
        when(maintenanceTicketRepository.findByIdAndTenantId(1L, 10L))
                .thenReturn(Optional.of(ticket));
    }

    private PostMaintenanceTest postTest(PostMaintenanceTestStatus status) {
        return PostMaintenanceTest.builder()
                .tenantId(10L)
                .ticketId(1L)
                .machineId(20L)
                .requestedByActorId(100L)
                .status(status)
                .build();
    }

    private CloseMaintenanceTicketRequest closeRequest() {
        CloseMaintenanceTicketRequest request = new CloseMaintenanceTicketRequest();
        request.setTenantId(10L);
        request.setClosedByActorId(100L);
        return request;
    }

    private ReopenMaintenanceTicketRequest reopenRequest() {
        ReopenMaintenanceTicketRequest request = new ReopenMaintenanceTicketRequest();
        request.setTenantId(10L);
        request.setReopenedByActorId(100L);
        request.setReason("Issue recurred");
        return request;
    }
}
