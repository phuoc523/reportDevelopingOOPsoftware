package com.scentstation;

import com.scentstation.MachineResponses.MachineAvailabilityResponse;
import com.scentstation.MachineResponses.SlotResponse;
import java.time.LocalTime;



import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AvailabilityService {
    private final MachineService machineService;
    private final SlotService slotService;
    private final MachineSlotRepository slots;

    public AvailabilityService(MachineService machineService, SlotService slotService,
                               MachineSlotRepository slots) {
        this.machineService = machineService;
        this.slotService = slotService;
        this.slots = slots;
    }

    static boolean isWithinOperatingHours(java.time.LocalTime start, java.time.LocalTime end, LocalTime now) {
        if (start == null || end == null) return true; // No schedule configured means always allowed.
        if (start.equals(end)) return false;
        if (start.isBefore(end)) return !now.isBefore(start) && now.isBefore(end);
        // Overnight schedule, e.g. 22:00-06:00.
        return !now.isBefore(start) || now.isBefore(end);
    }

    @Transactional(readOnly = true)
    public MachineAvailabilityResponse getAvailability(Long machineId) {
        Machine machine = machineService.requireMachine(machineId);
        List<SlotResponse> slotResponses = slots.findAllByMachineIdOrderBySlotNumberAsc(machineId)
            .stream().map(slot -> SlotResponse.from(slot, slotService.isAvailable(machine, slot))).toList();

        boolean withinHours = isWithinOperatingHours(machine.getOperatingHoursStart(), machine.getOperatingHoursEnd(), LocalTime.now());
        boolean machineAvailable = machine.getDeviceRegistrationStatus() == DeviceRegistrationStatus.ACTIVE
            && machine.getOperationalStatus() == MachineOperationalStatus.ONLINE && withinHours;
        String reason = machine.getDeviceRegistrationStatus() == DeviceRegistrationStatus.REVOKED
            ? "Device registration has been revoked"
            : machine.getOperationalStatus() != MachineOperationalStatus.ONLINE
                ? "Machine operational status is " + machine.getOperationalStatus()
                : !withinHours ? "Machine is outside operating hours" : "Machine is online and within operating hours";

        return new MachineAvailabilityResponse(machineId, machineAvailable,
            machine.getOperationalStatus(), reason, slotResponses);
    }
}
