package com.scentstation.operation.maintenance.controller;

import com.scentstation.operation.maintenance.dto.request.CreateMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.request.UpdateMaintenanceTicketRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceTicketResponse;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import com.scentstation.operation.maintenance.service.MaintenanceTicketService;
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
        if (status != null) {
            return ResponseEntity.ok(
                    maintenanceTicketService
                            .getTicketsByStatus(
                                    tenantId,
                                    status
                            )
            );
        }

        return ResponseEntity.ok(
                maintenanceTicketService
                        .getTickets(tenantId)
        );
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
}