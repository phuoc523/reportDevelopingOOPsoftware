package com.scentstation.operation.maintenance.controller;

import com.scentstation.operation.maintenance.dto.request.CompleteChecklistItemRequest;
import com.scentstation.operation.maintenance.dto.request.CreateChecklistItemRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceChecklistItemResponse;
import com.scentstation.operation.maintenance.service.MaintenanceChecklistService;
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
public class MaintenanceChecklistController {

    private final MaintenanceChecklistService
            maintenanceChecklistService;

    // Thêm checklist item vào maintenance ticket.
    @PostMapping("/{ticketId}/checklist")
    public ResponseEntity<MaintenanceChecklistItemResponse>
    createChecklistItem(
            @PathVariable @Positive Long ticketId,
            @Valid @RequestBody CreateChecklistItemRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        maintenanceChecklistService
                                .createChecklistItem(
                                        ticketId,
                                        request
                                )
                );
    }

    // Lấy toàn bộ checklist của một maintenance ticket.
    @GetMapping("/{ticketId}/checklist")
    public ResponseEntity<List<MaintenanceChecklistItemResponse>>
    getChecklist(
            @PathVariable @Positive Long ticketId,
            @RequestParam @Positive Long tenantId
    ) {
        return ResponseEntity.ok(
                maintenanceChecklistService
                        .getChecklist(
                                tenantId,
                                ticketId
                        )
        );
    }

        // Đánh dấu checklist item thuộc đúng ticket đã hoàn thành.
        @PostMapping(
                "/{ticketId}/checklist/{checklistItemId}/complete"
        )
        public ResponseEntity<MaintenanceChecklistItemResponse>
        completeChecklistItem(
                @PathVariable @Positive Long ticketId,
                @PathVariable @Positive Long checklistItemId,
                @Valid @RequestBody CompleteChecklistItemRequest request
        ) {
        return ResponseEntity.ok(
                maintenanceChecklistService
                        .completeChecklistItem(
                                ticketId,
                                checklistItemId,
                                request
                        )
        );
        }
}