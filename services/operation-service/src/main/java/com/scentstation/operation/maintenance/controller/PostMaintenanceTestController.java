package com.scentstation.operation.maintenance.controller;

import com.scentstation.operation.maintenance.dto.request.PostMaintenanceTestResultRequest;
import com.scentstation.operation.maintenance.dto.request.RequestPostMaintenanceTestRequest;
import com.scentstation.operation.maintenance.dto.response.PostMaintenanceTestResponse;
import com.scentstation.operation.maintenance.service.PostMaintenanceTestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
@Validated
public class PostMaintenanceTestController {

    private final PostMaintenanceTestService postMaintenanceTestService;

    // Yêu cầu Machine chạy post-maintenance test cho ticket.
    @PostMapping("/tickets/{ticketId}/post-tests")
    public ResponseEntity<PostMaintenanceTestResponse> requestTest(
            @PathVariable @Positive Long ticketId,
            @Valid @RequestBody RequestPostMaintenanceTestRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                postMaintenanceTestService.requestTest(ticketId, request)
        );
    }

    // Lấy lịch sử post-maintenance test của ticket trong tenant.
    @GetMapping("/tickets/{ticketId}/post-tests")
    public ResponseEntity<List<PostMaintenanceTestResponse>> getHistory(
            @PathVariable @Positive Long ticketId,
            @RequestParam @Positive Long tenantId
    ) {
        return ResponseEntity.ok(
                postMaintenanceTestService.getHistory(tenantId, ticketId)
        );
    }

    // Nhận kết quả cuối của post-maintenance test từ Machine integration.
    @PostMapping("/post-tests/results")
    public ResponseEntity<PostMaintenanceTestResponse> recordResult(
            @Valid @RequestBody PostMaintenanceTestResultRequest request
    ) {
        return ResponseEntity.ok(
                postMaintenanceTestService.recordResult(request)
        );
    }
}
