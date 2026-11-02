package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.dto.request.CompleteChecklistItemRequest;
import com.scentstation.operation.maintenance.dto.request.CreateChecklistItemRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceChecklistItemResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceChecklistItem;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.exception.InvalidMaintenanceTicketException;
import com.scentstation.operation.maintenance.exception.MaintenanceChecklistNotFoundException;
import com.scentstation.operation.maintenance.mapper.MaintenanceChecklistMapper;
import com.scentstation.operation.maintenance.repository.MaintenanceChecklistRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.scentstation.operation.maintenance.exception.MaintenanceChecklistNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceChecklistServiceTest {

    @Mock
    private MaintenanceChecklistRepository
            maintenanceChecklistRepository;

    @Mock
    private MaintenanceTicketRepository
            maintenanceTicketRepository;

    @Mock
    private MaintenanceChecklistMapper
            maintenanceChecklistMapper;

    @InjectMocks
    private MaintenanceChecklistService
            maintenanceChecklistService;

    private MaintenanceTicket ticket;

    private MaintenanceChecklistItem item;

    @BeforeEach
    void setUp() {
        ticket = MaintenanceTicket.builder()
                .id(1L)
                .tenantId(10L)
                .machineId(20L)
                .title("Maintenance")
                .description("Maintenance")
                .category("TEST")
                .severity(
                        MaintenanceSeverity.MODERATE
                )
                .priority(
                        MaintenancePriority.MEDIUM
                )
                .status(
                        MaintenanceStatus.IN_PROGRESS
                )
                .reportedByActorId(100L)
                .build();

        item = MaintenanceChecklistItem.builder()
                .id(5L)
                .tenantId(10L)
                .ticketId(1L)
                .itemName("Check pump")
                .required(true)
                .completed(false)
                .build();
    }

    @Test
    void shouldCreateChecklistItem() {
        CreateChecklistItemRequest request =
                new CreateChecklistItemRequest();

        request.setTenantId(10L);
        request.setItemName("Check pump");
        request.setRequired(true);

        MaintenanceChecklistItemResponse response =
                MaintenanceChecklistItemResponse.builder()
                        .id(5L)
                        .tenantId(10L)
                        .ticketId(1L)
                        .itemName("Check pump")
                        .required(true)
                        .completed(false)
                        .build();

        when(
                maintenanceTicketRepository
                        .findByIdAndTenantId(
                                1L,
                                10L
                        )
        ).thenReturn(Optional.of(ticket));

        when(
                maintenanceChecklistMapper
                        .toEntity(
                                1L,
                                request
                        )
        ).thenReturn(item);

        when(
                maintenanceChecklistRepository
                        .save(item)
        ).thenReturn(item);

        when(
                maintenanceChecklistMapper
                        .toResponse(item)
        ).thenReturn(response);

        MaintenanceChecklistItemResponse result =
                maintenanceChecklistService
                        .createChecklistItem(
                                1L,
                                request
                        );

        assertEquals(
                5L,
                result.getId()
        );

        assertTrue(result.isRequired());
    }

    @Test
    void shouldCompleteChecklistItem() {
        CompleteChecklistItemRequest request =
                new CompleteChecklistItemRequest();

        request.setTenantId(10L);
        request.setCompletedByActorId(200L);
        request.setResultNote("Pump checked");

        when(
                maintenanceChecklistRepository
                        .findByIdAndTenantId(
                                5L,
                                10L
                        )
        ).thenReturn(Optional.of(item));

        when(
                maintenanceTicketRepository
                        .findByIdAndTenantId(
                                1L,
                                10L
                        )
        ).thenReturn(Optional.of(ticket));

        when(
                maintenanceChecklistRepository
                        .save(item)
        ).thenReturn(item);

        maintenanceChecklistService
                .completeChecklistItem(
                        1L,
                        5L,
                        request
                );

        assertTrue(item.isCompleted());

        assertEquals(
                200L,
                item.getCompletedByActorId()
        );

        assertEquals(
                "Pump checked",
                item.getResultNote()
        );

        assertNotNull(
                item.getCompletedAt()
        );
    }

    @Test
    void shouldRejectAlreadyCompletedChecklistItem() {
        item.setCompleted(true);

        CompleteChecklistItemRequest request =
                new CompleteChecklistItemRequest();

        request.setTenantId(10L);
        request.setCompletedByActorId(200L);

        when(
                maintenanceChecklistRepository
                        .findByIdAndTenantId(
                                5L,
                                10L
                        )
        ).thenReturn(Optional.of(item));

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
                        maintenanceChecklistService
                                .completeChecklistItem(
                                        1L,
                                        5L,
                                        request
                                )
        );
    }

    @Test
    void shouldDetectIncompleteRequiredChecklist() {
        when(
                maintenanceTicketRepository
                        .findByIdAndTenantId(
                                1L,
                                10L
                        )
        ).thenReturn(Optional.of(ticket));

        when(
                maintenanceChecklistRepository
                        .existsByTenantIdAndTicketIdAndRequiredTrueAndCompletedFalse(
                                10L,
                                1L
                        )
        ).thenReturn(true);

        boolean result =
                maintenanceChecklistService
                        .hasIncompleteRequiredChecklist(
                                10L,
                                1L
                        );

        assertTrue(result);
    }

    @Test
    void shouldRejectChecklistChangesWhenTicketCompleted() {
        ticket.setStatus(
                MaintenanceStatus.COMPLETED
        );

        CreateChecklistItemRequest request =
                new CreateChecklistItemRequest();

        request.setTenantId(10L);
        request.setItemName("Check pump");

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
                        maintenanceChecklistService
                                .createChecklistItem(
                                        1L,
                                        request
                                )
        );

        verify(
                maintenanceChecklistRepository,
                never()
        ).save(any());
    }

    @Test
    void shouldRejectChecklistItemFromDifferentTicket() {
        item.setTicketId(99L);

        CompleteChecklistItemRequest request =
                new CompleteChecklistItemRequest();

        request.setTenantId(10L);
        request.setCompletedByActorId(200L);

        when(
                maintenanceChecklistRepository
                        .findByIdAndTenantId(
                                5L,
                                10L
                        )
        ).thenReturn(Optional.of(item));

        assertThrows(
                MaintenanceChecklistNotFoundException.class,
                () ->
                        maintenanceChecklistService
                                .completeChecklistItem(
                                        1L,
                                        5L,
                                        request
                                )
        );

        verify(
                maintenanceChecklistRepository,
                never()
        ).save(any());
    }
}