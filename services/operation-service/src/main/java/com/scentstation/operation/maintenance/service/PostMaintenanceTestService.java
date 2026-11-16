package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.dto.request.PostMaintenanceTestResultRequest;
import com.scentstation.operation.maintenance.dto.request.RequestPostMaintenanceTestRequest;
import com.scentstation.operation.maintenance.dto.response.PostMaintenanceTestResponse;
import com.scentstation.operation.maintenance.entity.MaintenanceAuditEvent;
import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.entity.PostMaintenanceTest;
import com.scentstation.operation.maintenance.enums.MaintenanceAuditAction;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.enums.PostMaintenanceTestStatus;
import com.scentstation.operation.maintenance.exception.InvalidMaintenanceTicketException;
import com.scentstation.operation.maintenance.exception.MaintenanceNotFoundException;
import com.scentstation.operation.maintenance.exception.PostMaintenanceTestNotFoundException;
import com.scentstation.operation.maintenance.integration.PostMaintenanceMachineClient;
import com.scentstation.operation.maintenance.mapper.PostMaintenanceTestMapper;
import com.scentstation.operation.maintenance.repository.MaintenanceChecklistRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceRepairLogRepository;
import com.scentstation.operation.maintenance.repository.MaintenanceTicketRepository;
import com.scentstation.operation.maintenance.repository.PostMaintenanceTestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PostMaintenanceTestService {

    private final MaintenanceTicketRepository maintenanceTicketRepository;
    private final MaintenanceRepairLogRepository maintenanceRepairLogRepository;
    private final MaintenanceChecklistRepository maintenanceChecklistRepository;
    private final PostMaintenanceTestRepository postMaintenanceTestRepository;
    private final Optional<PostMaintenanceMachineClient> postMaintenanceMachineClient;
    private final PostMaintenanceTestMapper postMaintenanceTestMapper;
    private final MaintenanceAuditService maintenanceAuditService;

    // Tạo yêu cầu test hợp lệ và chuyển lệnh sang Machine trong cùng transaction.
    @Transactional
    public PostMaintenanceTestResponse requestTest(
            Long ticketId,
            RequestPostMaintenanceTestRequest request
    ) {
        MaintenanceTicket ticket = findTicket(request.getTenantId(), ticketId);
        validateRequest(ticket);

        PostMaintenanceTest test = PostMaintenanceTest.builder()
                .tenantId(ticket.getTenantId())
                .ticketId(ticket.getId())
                .machineId(ticket.getMachineId())
                .requestedByActorId(request.getRequestedByActorId())
                .status(PostMaintenanceTestStatus.REQUESTED)
                .build();

        PostMaintenanceTest saved = postMaintenanceTestRepository.saveAndFlush(test);
        String externalTestId = postMaintenanceMachineClient
                .orElseThrow(() -> new IllegalStateException(
                        "Post-maintenance Machine integration is not configured"
                ))
                .requestPostMaintenanceTest(
                        ticket.getTenantId(),
                        ticket.getMachineId(),
                        ticket.getId()
                );
        saved.setExternalTestId(externalTestId);

        PostMaintenanceTest persisted = postMaintenanceTestRepository.save(saved);
        recordAudit(
                persisted,
                MaintenanceAuditAction.POST_TEST_REQUESTED,
                persisted.getRequestedByActorId(),
                null,
                "status=" + persisted.getStatus()
        );

        return postMaintenanceTestMapper.toResponse(persisted);
    }

    // Ghi nhận một kết quả cuối PASSED hoặc FAILED từ Machine.
    @Transactional
    public PostMaintenanceTestResponse recordResult(
            PostMaintenanceTestResultRequest request
    ) {
        validateFinalStatus(request.getStatus());

        PostMaintenanceTest test = postMaintenanceTestRepository
                .findByTenantIdAndExternalTestId(
                        request.getTenantId(),
                        request.getExternalTestId()
                )
                .orElseThrow(() -> new PostMaintenanceTestNotFoundException(
                        request.getExternalTestId()
                ));

        if (isFinal(test.getStatus())) {
            throw new InvalidMaintenanceTicketException(
                    "Post-maintenance test already has a final result"
            );
        }

        PostMaintenanceTestStatus beforeStatus = test.getStatus();
        test.setStatus(request.getStatus());
        test.setResultMessage(request.getResultMessage());
        test.setCompletedAt(LocalDateTime.now());

        PostMaintenanceTest saved = postMaintenanceTestRepository.save(test);
        recordAudit(
                saved,
                request.getStatus() == PostMaintenanceTestStatus.PASSED
                        ? MaintenanceAuditAction.POST_TEST_PASSED
                        : MaintenanceAuditAction.POST_TEST_FAILED,
                null,
                "status=" + beforeStatus,
                "status=" + saved.getStatus()
        );

        return postMaintenanceTestMapper.toResponse(saved);
    }

    // Lấy lịch sử test của ticket sau khi xác nhận ticket thuộc đúng tenant.
    @Transactional(readOnly = true)
    public List<PostMaintenanceTestResponse> getHistory(
            Long tenantId,
            Long ticketId
    ) {
        findTicket(tenantId, ticketId);
        return postMaintenanceTestRepository
                .findAllByTenantIdAndTicketIdOrderByRequestedAtAsc(
                        tenantId,
                        ticketId
                )
                .stream()
                .map(postMaintenanceTestMapper::toResponse)
                .toList();
    }

    // Kiểm tra ticket đã có ít nhất một post-maintenance test PASSED hay chưa.
    @Transactional(readOnly = true)
    public boolean hasPassedTest(Long tenantId, Long ticketId) {
        findTicket(tenantId, ticketId);
        return postMaintenanceTestRepository
                .existsByTenantIdAndTicketIdAndStatus(
                        tenantId,
                        ticketId,
                        PostMaintenanceTestStatus.PASSED
                );
    }

    // Tìm maintenance ticket trong phạm vi tenant.
    private MaintenanceTicket findTicket(Long tenantId, Long ticketId) {
        return maintenanceTicketRepository
                .findByIdAndTenantId(ticketId, tenantId)
                .orElseThrow(() -> new MaintenanceNotFoundException(ticketId));
    }

    // Chặn yêu cầu test khi ticket hoặc dữ liệu bảo trì chưa đủ điều kiện.
    private void validateRequest(MaintenanceTicket ticket) {
        if (ticket.getStatus() == MaintenanceStatus.COMPLETED
                || ticket.getStatus() == MaintenanceStatus.CANCELLED) {
            throw new InvalidMaintenanceTicketException(
                    "Completed or cancelled ticket cannot request a post-maintenance test"
            );
        }

        if (!maintenanceRepairLogRepository.existsByTenantIdAndTicketId(
                ticket.getTenantId(),
                ticket.getId()
        )) {
            throw new InvalidMaintenanceTicketException(
                    "Post-maintenance test requires a repair result"
            );
        }

        if (maintenanceChecklistRepository
                .existsByTenantIdAndTicketIdAndRequiredTrueAndCompletedFalse(
                        ticket.getTenantId(),
                        ticket.getId()
                )) {
            throw new InvalidMaintenanceTicketException(
                    "Required maintenance checklist is incomplete"
            );
        }
    }

    // Chỉ chấp nhận trạng thái kết quả cuối từ Machine.
    private void validateFinalStatus(PostMaintenanceTestStatus status) {
        if (!isFinal(status)) {
            throw new InvalidMaintenanceTicketException(
                    "Post-maintenance result status must be PASSED or FAILED"
            );
        }
    }

    // Xác định trạng thái test đã bất biến hay chưa.
    private boolean isFinal(PostMaintenanceTestStatus status) {
        return status == PostMaintenanceTestStatus.PASSED
                || status == PostMaintenanceTestStatus.FAILED;
    }

    // Ghi audit cho yêu cầu và kết quả post-maintenance test.
    private void recordAudit(
            PostMaintenanceTest test,
            MaintenanceAuditAction action,
            Long actorId,
            String beforeState,
            String afterState
    ) {
        maintenanceAuditService.record(
                MaintenanceAuditEvent.builder()
                        .tenantId(test.getTenantId())
                        .action(action)
                        .entityType("POST_MAINTENANCE_TEST")
                        .entityId(test.getId())
                        .actorId(actorId)
                        .ticketId(test.getTicketId())
                        .machineId(test.getMachineId())
                        .beforeState(beforeState)
                        .afterState(afterState)
                        .source("POST_MAINTENANCE_TEST")
                        .build()
        );
    }
}
