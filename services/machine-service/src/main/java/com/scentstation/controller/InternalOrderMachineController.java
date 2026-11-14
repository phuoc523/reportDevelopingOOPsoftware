package com.scentstation.controller;

import com.scentstation.dto.response.MachineResponses.MachineAvailabilityResponse;
import com.scentstation.dto.response.MachineResponses.SlotResponse;
import com.scentstation.dto.response.OrderMachineSlotResponse;
import com.scentstation.entity.Machine;
import com.scentstation.exception.InvalidOperationException;
import com.scentstation.service.AvailabilityService;
import com.scentstation.service.InternalServiceKeyVerifier;
import com.scentstation.service.MachineService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/machines")
public class InternalOrderMachineController {
    private final MachineService machines;
    private final AvailabilityService availability;
    private final InternalServiceKeyVerifier keys;

    public InternalOrderMachineController(MachineService machines, AvailabilityService availability,
                                          InternalServiceKeyVerifier keys) {
        this.machines = machines;
        this.availability = availability;
        this.keys = keys;
    }

    @GetMapping("/{machineId}/slots/{slotId}")
    public OrderMachineSlotResponse get(@PathVariable Long machineId, @PathVariable Long slotId,
                                        @RequestParam String tenantId,
                                        @RequestHeader(value = "X-Internal-Service-Key", required = false) String key) {
        keys.verify(key);
        Machine machine = machines.requireMachine(machineId);
        if (!machine.getTenantId().equals(tenantId)) {
            throw new InvalidOperationException("Machine does not belong to the authenticated tenant");
        }
        MachineAvailabilityResponse state = availability.getAvailability(machineId);
        SlotResponse slot = state.getSlots().stream().filter(value -> value.getId().equals(slotId))
                .findFirst().orElseThrow(() -> new com.scentstation.exception.ResourceNotFoundException(
                        "Slot not found on machine: " + slotId));
        return new OrderMachineSlotResponse(machineId, machine.getTenantId(), slotId, slot.getProductId(),
                state.isMachineAvailable(), slot.isAvailable(),
                state.isMachineAvailable() ? slot.getAvailabilityReason() : state.getReason());
    }
}
