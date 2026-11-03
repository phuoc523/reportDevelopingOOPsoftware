package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.dto.request.PostMaintenanceTestResultRequest;
import com.scentstation.operation.maintenance.dto.request.RequestPostMaintenanceTestRequest;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.entity.PostMaintenanceTest;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.enums.PostMaintenanceTestStatus;
import com.scentstation.operation.maintenance.exception.InvalidMaintenanceTicketException;
import com.scentstation.operation.maintenance.exception.PostMaintenanceTestNotFoundException;
import com.scentstation.operation.maintenance.integration.PostMaintenanceMachineClient;
import com.scentstation.operation.maintenance.mapper.PostMaintenanceTestMapper;
import com.scentstation.operation.maintenance.repository.MaintenanceChecklistRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceRepairLogRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import com.scentstation.operation.maintenance.repository.PostMaintenanceTestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostMaintenanceTestServiceTest {

    private MaintenanceTicketRepository ticketRepository;
    private MaintenanceRepairLogRepository repairLogRepository;
    private MaintenanceChecklistRepository checklistRepository;
    private PostMaintenanceTestRepository testRepository;
    private PostMaintenanceMachineClient machineClient;
    private PostMaintenanceTestService service;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(MaintenanceTicketRepository.class);
        repairLogRepository = mock(MaintenanceRepairLogRepository.class);
        checklistRepository = mock(MaintenanceChecklistRepository.class);
        testRepository = mock(PostMaintenanceTestRepository.class);
        machineClient = mock(PostMaintenanceMachineClient.class);
        service = new PostMaintenanceTestService(
                ticketRepository,
                repairLogRepository,
                checklistRepository,
                testRepository,
                Optional.of(machineClient),
                new PostMaintenanceTestMapper()
        );
    }

    @Test
    void shouldRequestTestUsingMachineFromTicket() {
        MaintenanceTicket ticket = ticket(MaintenanceStatus.IN_PROGRESS);
        when(ticketRepository.findByIdAndTenantId(7L, 1L))
                .thenReturn(Optional.of(ticket));
        when(repairLogRepository.existsByTenantIdAndTicketId(1L, 7L))
                .thenReturn(true);
        when(checklistRepository
                .existsByTenantIdAndTicketIdAndRequiredTrueAndCompletedFalse(1L, 7L))
                .thenReturn(false);
        when(testRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(testRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(machineClient.requestPostMaintenanceTest(1L, 99L, 7L))
                .thenReturn("EXT-1");

        RequestPostMaintenanceTestRequest request = request();
        var response = service.requestTest(7L, request);

        assertEquals(99L, response.getMachineId());
        assertEquals(PostMaintenanceTestStatus.REQUESTED, response.getStatus());
        assertEquals("EXT-1", response.getExternalTestId());
        verify(machineClient).requestPostMaintenanceTest(1L, 99L, 7L);

        ArgumentCaptor<PostMaintenanceTest> captor =
                ArgumentCaptor.forClass(PostMaintenanceTest.class);
        verify(testRepository).save(captor.capture());
        assertEquals(5L, captor.getValue().getRequestedByActorId());
    }

    @Test
    void shouldRejectRequestWithoutRepairResult() {
        stubTicket(MaintenanceStatus.IN_PROGRESS);
        assertThrows(
                InvalidMaintenanceTicketException.class,
                () -> service.requestTest(7L, request())
        );
        verifyNoInteractions(machineClient);
    }

    @Test
    void shouldRejectRequestWithIncompleteRequiredChecklist() {
        stubEligibleRepair(MaintenanceStatus.IN_PROGRESS);
        when(checklistRepository
                .existsByTenantIdAndTicketIdAndRequiredTrueAndCompletedFalse(1L, 7L))
                .thenReturn(true);
        assertThrows(
                InvalidMaintenanceTicketException.class,
                () -> service.requestTest(7L, request())
        );
    }

    @ParameterizedTest
    @EnumSource(value = MaintenanceStatus.class, names = {"COMPLETED", "CANCELLED"})
    void shouldRejectRequestForFinalTicket(MaintenanceStatus status) {
        stubTicket(status);
        assertThrows(
                InvalidMaintenanceTicketException.class,
                () -> service.requestTest(7L, request())
        );
        verifyNoInteractions(repairLogRepository, machineClient);
    }

    @ParameterizedTest
    @EnumSource(value = PostMaintenanceTestStatus.class, names = {"PASSED", "FAILED"})
    void shouldRecordFinalResult(PostMaintenanceTestStatus status) {
        PostMaintenanceTest test = postTest(PostMaintenanceTestStatus.REQUESTED);
        when(testRepository.findByTenantIdAndExternalTestId(1L, "EXT-1"))
                .thenReturn(Optional.of(test));
        when(testRepository.save(test)).thenReturn(test);

        var response = service.recordResult(result(status));

        assertEquals(status, response.getStatus());
        assertEquals("result", response.getResultMessage());
        assertNotNull(response.getCompletedAt());
    }

    @ParameterizedTest
    @EnumSource(value = PostMaintenanceTestStatus.class, names = {"REQUESTED", "RUNNING"})
    void shouldRejectNonFinalResult(PostMaintenanceTestStatus status) {
        assertThrows(
                InvalidMaintenanceTicketException.class,
                () -> service.recordResult(result(status))
        );
        verifyNoInteractions(testRepository);
    }

    @ParameterizedTest
    @EnumSource(value = PostMaintenanceTestStatus.class, names = {"PASSED", "FAILED"})
    void shouldRejectSecondFinalResult(PostMaintenanceTestStatus currentStatus) {
        when(testRepository.findByTenantIdAndExternalTestId(1L, "EXT-1"))
                .thenReturn(Optional.of(postTest(currentStatus)));
        assertThrows(
                InvalidMaintenanceTicketException.class,
                () -> service.recordResult(result(PostMaintenanceTestStatus.PASSED))
        );
        verify(testRepository, never()).save(any());
    }

    @Test
    void shouldRejectCrossTenantExternalIdAsNotFound() {
        when(testRepository.findByTenantIdAndExternalTestId(2L, "EXT-1"))
                .thenReturn(Optional.empty());
        PostMaintenanceTestResultRequest request = result(PostMaintenanceTestStatus.PASSED);
        request.setTenantId(2L);
        assertThrows(
                PostMaintenanceTestNotFoundException.class,
                () -> service.recordResult(request)
        );
    }

    @Test
    void shouldGetTenantScopedHistory() {
        stubTicket(MaintenanceStatus.IN_PROGRESS);
        when(testRepository.findAllByTenantIdAndTicketIdOrderByRequestedAtAsc(1L, 7L))
                .thenReturn(List.of(postTest(PostMaintenanceTestStatus.PASSED)));
        assertEquals(1, service.getHistory(1L, 7L).size());
        verify(testRepository)
                .findAllByTenantIdAndTicketIdOrderByRequestedAtAsc(1L, 7L);
    }

    @Test
    void shouldReportPassedTestExists() {
        stubTicket(MaintenanceStatus.IN_PROGRESS);
        when(testRepository.existsByTenantIdAndTicketIdAndStatus(
                1L, 7L, PostMaintenanceTestStatus.PASSED
        )).thenReturn(true);
        assertTrue(service.hasPassedTest(1L, 7L));
    }

    private void stubTicket(MaintenanceStatus status) {
        when(ticketRepository.findByIdAndTenantId(7L, 1L))
                .thenReturn(Optional.of(ticket(status)));
    }

    private void stubEligibleRepair(MaintenanceStatus status) {
        stubTicket(status);
        when(repairLogRepository.existsByTenantIdAndTicketId(1L, 7L))
                .thenReturn(true);
    }

    private MaintenanceTicket ticket(MaintenanceStatus status) {
        return MaintenanceTicket.builder()
                .id(7L)
                .tenantId(1L)
                .machineId(99L)
                .status(status)
                .build();
    }

    private RequestPostMaintenanceTestRequest request() {
        RequestPostMaintenanceTestRequest request = new RequestPostMaintenanceTestRequest();
        request.setTenantId(1L);
        request.setRequestedByActorId(5L);
        return request;
    }

    private PostMaintenanceTestResultRequest result(PostMaintenanceTestStatus status) {
        PostMaintenanceTestResultRequest request = new PostMaintenanceTestResultRequest();
        request.setTenantId(1L);
        request.setExternalTestId("EXT-1");
        request.setStatus(status);
        request.setResultMessage("result");
        return request;
    }

    private PostMaintenanceTest postTest(PostMaintenanceTestStatus status) {
        return PostMaintenanceTest.builder()
                .id(11L)
                .tenantId(1L)
                .ticketId(7L)
                .machineId(99L)
                .requestedByActorId(5L)
                .externalTestId("EXT-1")
                .status(status)
                .build();
    }
}
