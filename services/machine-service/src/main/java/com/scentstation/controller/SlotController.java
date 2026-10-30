package com.scentstation.controller;

import com.scentstation.dto.request.MachineRequests.*;
import com.scentstation.dto.response.MachineResponses.*;
import com.scentstation.entity.SlotStatus;
import com.scentstation.service.SlotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/machines/{machineId}/slots")
public class SlotController {
    private final SlotService slotService;

    public SlotController(SlotService slotService) { this.slotService = slotService; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SlotResponse create(@PathVariable @NonNull Long machineId,
                               @Valid @RequestBody CreateSlotRequest request,
                               @RequestHeader(value = "X-Actor-Id", required = false) String actorId) {
        return slotService.create(machineId, request, actorId);
    }

    @GetMapping
    public List<SlotResponse> list(@PathVariable @NonNull Long machineId) {
        return slotService.list(machineId);
    }

    @GetMapping("/{slotId}")
    public SlotResponse get(@PathVariable @NonNull Long machineId,
                            @PathVariable @NonNull Long slotId) {
        return slotService.get(machineId, slotId);
    }

    @PutMapping("/{slotId}")
    public SlotResponse update(@PathVariable @NonNull Long machineId,
                               @PathVariable @NonNull Long slotId,
                               @Valid @RequestBody UpdateSlotRequest request,
                               @RequestHeader(value = "X-Actor-Id", required = false) String actorId) {
        return slotService.update(machineId, slotId, request, actorId);
    }

    @PatchMapping("/{slotId}/enabled")
    public SlotResponse setEnabled(@PathVariable @NonNull Long machineId,
                                   @PathVariable @NonNull Long slotId,
                                   @Valid @RequestBody UpdateSlotEnabledRequest request,
                                   @RequestHeader(value = "X-Actor-Id", required = false) String actorId) {
        return slotService.setEnabled(machineId, slotId, request, actorId);
    }

    @PatchMapping("/{slotId}/status")
    public SlotResponse updateStatus(@PathVariable @NonNull Long machineId,
                                     @PathVariable @NonNull Long slotId,
                                     @RequestParam SlotStatus status,
                                     @RequestHeader(value = "X-Actor-Id", required = false) String actorId) {
        return slotService.updateReportedStatus(machineId, slotId, status, actorId);
    }
}
