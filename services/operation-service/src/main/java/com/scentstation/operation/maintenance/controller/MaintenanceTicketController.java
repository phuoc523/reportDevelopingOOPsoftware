package com.scentstation.operation.maintenance.controller;

import com.scentstation.operation.maintenance.dto.request.CloseMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.request.CreateMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.request.ReopenMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.request.UpdateMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceTicketResponse;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.service.MaintenanceTicketService;
import com.scentstation.operation.integration.identity.dto.IdentityPrincipal;
import com.scentstation.operation.security.OperationAuthorization;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maintenance/tickets")
@RequiredArgsConstructor
@Validated
public class MaintenanceTicketController {

    private final MaintenanceTicketService maintenanceTicketService;
    private final OperationAuthorization authorization;
    private final HttpServletRequest httpRequest;

    // Tạo một maintenance ticket mới.
    @PostMapping
    public ResponseEntity<MaintenanceTicketResponse> createTicket(
            @Valid @RequestBody CreateMaintenanceTicketRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        maintenanceTicketService
                                .createTicket(request)
                );
    }

    // Lấy danh sách ticket theo tenant, có thể lọc theo trạng thái.
    @GetMapping
    public ResponseEntity<List<MaintenanceTicketResponse>> getTickets(
            @RequestParam @Positive Long tenantId,
            @RequestParam(required = false) MaintenanceStatus status
    ) {
        List<MaintenanceTicketResponse> tickets;
        if (status != null) {
            tickets = maintenanceTicketService.getTicketsByStatus(tenantId, status);
        } else {
            tickets = maintenanceTicketService.getTickets(tenantId);
        }
        return ResponseEntity.ok(authorization.filterTickets(currentActor(), tickets));
    }

    private IdentityPrincipal currentActor() {
        return (IdentityPrincipal) httpRequest.getAttribute(OperationAuthorization.PRINCIPAL_ATTRIBUTE);
    }

    // Lấy một maintenance ticket theo id trong tenant.
    @GetMapping("/{ticketId}")
    public ResponseEntity<MaintenanceTicketResponse> getTicket(
            @PathVariable @Positive Long ticketId,
            @RequestParam @Positive Long tenantId
    ) {
        return ResponseEntity.ok(
                maintenanceTicketService
                        .getTicketById(
                                tenantId,
                                ticketId
                        )
        );
    }

    // Cập nhật thông tin cơ bản của maintenance ticket.
    @PutMapping("/{ticketId}")
    public ResponseEntity<MaintenanceTicketResponse> updateTicket(
            @PathVariable @Positive Long ticketId,
            @RequestParam @Positive Long tenantId,
            @Valid @RequestBody UpdateMaintenanceTicketRequest request
    ) {
        return ResponseEntity.ok(
                maintenanceTicketService
                        .updateTicket(
                                tenantId,
                                ticketId,
                                request
                        )
        );
    }

    // Đóng maintenance ticket qua workflow kiểm tra điều kiện chuyên biệt.
    @PostMapping("/{ticketId}/close")
    public ResponseEntity<MaintenanceTicketResponse> closeTicket(
            @PathVariable @Positive Long ticketId,
            @Valid @RequestBody CloseMaintenanceTicketRequest request
    ) {
        return ResponseEntity.ok(
                maintenanceTicketService.closeTicket(ticketId, request)
        );
    }

    // Mở lại maintenance ticket đã hoàn thành để tiếp tục xử lý.
    @PostMapping("/{ticketId}/reopen")
    public ResponseEntity<MaintenanceTicketResponse> reopenTicket(
            @PathVariable @Positive Long ticketId,
            @Valid @RequestBody ReopenMaintenanceTicketRequest request
    ) {
        return ResponseEntity.ok(
                maintenanceTicketService.reopenTicket(ticketId, request)
        );
    }
}
