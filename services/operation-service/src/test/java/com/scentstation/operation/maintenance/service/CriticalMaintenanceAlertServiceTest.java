package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.dto.request.CriticalMaintenanceAlertRequest;
import com.scentstation.operation.maintenance.dto.response.CriticalMaintenanceAlertResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceAlertLink;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.exception.CriticalAlertAlreadyProcessedException;
import com.scentstation.operation.maintenance.repository.MaintenanceAlertLinkRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CriticalMaintenanceAlertServiceTest {

    @Mock
    private MaintenanceAlertLinkRepository
            maintenanceAlertLinkRepository;

    @Mock
    private MaintenanceTicketRepository
            maintenanceTicketRepository;

    @InjectMocks
    private CriticalMaintenanceAlertService
            criticalMaintenanceAlertService;

    private CriticalMaintenanceAlertRequest request;

    @BeforeEach
    void setUp() {
        request =
                new CriticalMaintenanceAlertRequest();

        request.setTenantId(1L);
        request.setMachineId(10L);
        request.setLocationId(20L);
        request.setSlotId(30L);
        request.setAlertId("ALERT-001");
        request.setAlertType("PUMP_FAILURE");
        request.setMessage(
                "Pump pressure reached critical level"
        );
        request.setOccurredAt(
                LocalDateTime.now()
        );
    }

    @Test
    void shouldCreateCriticalMaintenanceTicket() {
        when(
                maintenanceAlertLinkRepository
                        .existsByTenantIdAndSourceAlertId(
                                1L,
                                "ALERT-001"
                        )
        ).thenReturn(false);

        when(
                maintenanceTicketRepository.save(any())
        ).thenAnswer(invocation -> {
            MaintenanceTicket ticket =
                    invocation.getArgument(0);

            ticket.setId(100L);

            return ticket;
        });

        when(
                maintenanceAlertLinkRepository
                        .saveAndFlush(any())
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        CriticalMaintenanceAlertResponse response =
                criticalMaintenanceAlertService
                        .processCriticalAlert(request);

        assertEquals(
                100L,
                response.getTicketId()
        );

        assertEquals(
                MaintenanceSeverity.CRITICAL,
                response.getSeverity()
        );

        assertEquals(
                MaintenancePriority.URGENT,
                response.getPriority()
        );

        assertEquals(
                MaintenanceStatus.OPEN,
                response.getStatus()
        );

        ArgumentCaptor<MaintenanceTicket> ticketCaptor =
                ArgumentCaptor.forClass(
                        MaintenanceTicket.class
                );

        verify(
                maintenanceTicketRepository
        ).save(
                ticketCaptor.capture()
        );

        MaintenanceTicket savedTicket =
                ticketCaptor.getValue();

        assertTrue(
                savedTicket.isCritical()
        );

        assertEquals(
                "PUMP_FAILURE",
                savedTicket.getCategory()
        );

        assertEquals(
                "Pump pressure reached critical level",
                savedTicket.getDescription()
        );

        assertEquals(
                1L,
                savedTicket.getTenantId()
        );

        assertEquals(
                10L,
                savedTicket.getMachineId()
        );

        assertEquals(
                20L,
                savedTicket.getLocationId()
        );

        assertEquals(
                30L,
                savedTicket.getSlotId()
        );

        assertEquals(
                MaintenanceStatus.OPEN,
                savedTicket.getStatus()
        );
    }

    @Test
    void shouldCreateAlertLink() {
        when(
                maintenanceAlertLinkRepository
                        .existsByTenantIdAndSourceAlertId(
                                1L,
                                "ALERT-001"
                        )
        ).thenReturn(false);

        when(
                maintenanceTicketRepository.save(any())
        ).thenAnswer(invocation -> {
            MaintenanceTicket ticket =
                    invocation.getArgument(0);

            ticket.setId(100L);

            return ticket;
        });

        when(
                maintenanceAlertLinkRepository
                        .saveAndFlush(any())
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        criticalMaintenanceAlertService
                .processCriticalAlert(request);

        ArgumentCaptor<MaintenanceAlertLink> captor =
                ArgumentCaptor.forClass(
                        MaintenanceAlertLink.class
                );

        verify(
                maintenanceAlertLinkRepository
        ).saveAndFlush(
                captor.capture()
        );

        MaintenanceAlertLink link =
                captor.getValue();

        assertEquals(
                1L,
                link.getTenantId()
        );

        assertEquals(
                "ALERT-001",
                link.getSourceAlertId()
        );

        assertEquals(
                "PUMP_FAILURE",
                link.getAlertType()
        );

        assertEquals(
                100L,
                link.getTicketId()
        );

        assertEquals(
                10L,
                link.getMachineId()
        );

        assertEquals(
                request.getOccurredAt(),
                link.getOccurredAt()
        );
    }

    @Test
    void shouldConvertDatabaseDuplicateIntoDomainException() {
        when(
                maintenanceAlertLinkRepository
                        .existsByTenantIdAndSourceAlertId(
                                1L,
                                "ALERT-001"
                        )
        ).thenReturn(false);

        when(
                maintenanceTicketRepository.save(any())
        ).thenAnswer(invocation -> {
            MaintenanceTicket ticket =
                    invocation.getArgument(0);

            ticket.setId(100L);

            return ticket;
        });

        when(
                maintenanceAlertLinkRepository
                        .saveAndFlush(any())
        ).thenThrow(
                new DataIntegrityViolationException(
                        "Duplicate alert"
                )
        );

        assertThrows(
                CriticalAlertAlreadyProcessedException.class,
                () ->
                        criticalMaintenanceAlertService
                                .processCriticalAlert(request)
        );

        verify(
                maintenanceTicketRepository,
                times(1)
        ).save(any());

        verify(
                maintenanceAlertLinkRepository,
                times(1)
        ).saveAndFlush(any());
    }

    @Test
    void shouldRejectDuplicateCriticalAlertBeforeCreatingTicket() {
        when(
                maintenanceAlertLinkRepository
                        .existsByTenantIdAndSourceAlertId(
                                1L,
                                "ALERT-001"
                        )
        ).thenReturn(true);

        assertThrows(
                CriticalAlertAlreadyProcessedException.class,
                () ->
                        criticalMaintenanceAlertService
                                .processCriticalAlert(request)
        );

        verify(
                maintenanceTicketRepository,
                never()
        ).save(any());

        verify(
                maintenanceAlertLinkRepository,
                never()
        ).saveAndFlush(any());
    }

    @Test
    void shouldAllowSameAlertIdForDifferentTenant() {
        request.setTenantId(2L);

        when(
                maintenanceAlertLinkRepository
                        .existsByTenantIdAndSourceAlertId(
                                2L,
                                "ALERT-001"
                        )
        ).thenReturn(false);

        when(
                maintenanceTicketRepository.save(any())
        ).thenAnswer(invocation -> {
            MaintenanceTicket ticket =
                    invocation.getArgument(0);

            ticket.setId(200L);

            return ticket;
        });

        when(
                maintenanceAlertLinkRepository
                        .saveAndFlush(any())
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        CriticalMaintenanceAlertResponse response =
                criticalMaintenanceAlertService
                        .processCriticalAlert(request);

        assertEquals(
                2L,
                response.getTenantId()
        );

        assertEquals(
                200L,
                response.getTicketId()
        );
    }
}
