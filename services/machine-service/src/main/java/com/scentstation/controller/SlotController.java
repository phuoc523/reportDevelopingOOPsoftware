package com.scentstation.controller;

import com.scentstation.dto.request.MachineRequests.*;
import com.scentstation.dto.response.MachineResponses.*;
import com.scentstation.entity.SlotStatus;
import com.scentstation.service.SlotService;
import com.scentstation.security.MachineAuthorization;
import com.scentstation.security.MachineCurrentActorContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/machines/{machineId}/slots")
public class SlotController {
    private final SlotService slotService;
    private final MachineCurrentActorContext actors;
    private final MachineAuthorization authorization;

    public SlotController(SlotService slotService, MachineCurrentActorContext actors,
                          MachineAuthorization authorization) {
        this.slotService = slotService;
        this.actors = actors;
        this.authorization = authorization;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SlotResponse create(@PathVariable @NonNull Long machineId,
                               @Valid @RequestBody CreateSlotRequest request) {
        var actor = actors.principal();
        authorization.requireMachine(actor, machineId, MachineAuthorization.MACHINE_CONFIGURE);
        return slotService.create(machineId, request, actor.id().toString());
    }

    @GetMapping
    public List<SlotResponse> list(@PathVariable @NonNull Long machineId) {
        authorization.requireMachine(actors.principal(), machineId, MachineAuthorization.MACHINE_READ);
        return slotService.list(machineId);
    }

    @GetMapping("/{slotId}")
    public SlotResponse get(@PathVariable @NonNull Long machineId,
                            @PathVariable @NonNull Long slotId) {
        authorization.requireMachine(actors.principal(), machineId, MachineAuthorization.MACHINE_READ);
        return slotService.get(machineId, slotId);
    }

    @PutMapping("/{slotId}")
    public SlotResponse update(@PathVariable @NonNull Long machineId,
                               @PathVariable @NonNull Long slotId,
                               @Valid @RequestBody UpdateSlotRequest request) {
        var actor = actors.principal();
        authorization.requireMachine(actor, machineId, MachineAuthorization.MACHINE_CONFIGURE);
        return slotService.update(machineId, slotId, request, actor.id().toString());
    }

    @PatchMapping("/{slotId}/enabled")
    public SlotResponse setEnabled(@PathVariable @NonNull Long machineId,
                                   @PathVariable @NonNull Long slotId,
                                   @Valid @RequestBody UpdateSlotEnabledRequest request) {
        var actor = actors.principal();
        authorization.requireMachine(actor, machineId, MachineAuthorization.MACHINE_CONFIGURE);
        return slotService.setEnabled(machineId, slotId, request, actor.id().toString());
    }

    @PatchMapping("/{slotId}/status")
    public SlotResponse updateStatus(@PathVariable @NonNull Long machineId,
                                     @PathVariable @NonNull Long slotId,
                                     @RequestParam SlotStatus status) {
        var actor = actors.principal();
        authorization.requireMachine(actor, machineId, MachineAuthorization.MACHINE_CONFIGURE);
        return slotService.updateReportedStatus(machineId, slotId, status, actor.id().toString());
    }
}
