package com.scentstation.service;

import com.scentstation.dto.response.MachineResponses.MachineAvailabilityResponse;
import com.scentstation.dto.response.MachineResponses.SlotResponse;
import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.Machine;
import com.scentstation.entity.MachineOperationalStatus;
import com.scentstation.repository.MachineSlotRepository;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class AvailabilityService {
    private final MachineService machineService;
    private final SlotService slotService;
    private final MachineSlotRepository slots;
    private final ZoneId operatingHoursZone;

    public AvailabilityService(MachineService machineService, SlotService slotService,
                               MachineSlotRepository slots,
                               @Value("${machine.operating-hours.zone:Asia/Ho_Chi_Minh}") String operatingHoursZone) {
        this.machineService = machineService;
        this.slotService = slotService;
        this.slots = slots;
        this.operatingHoursZone = ZoneId.of(operatingHoursZone);
    }

    @Transactional(readOnly = true)
    public MachineAvailabilityResponse getAvailability(Long machineId) {
        Machine machine = machineService.requireMachine(machineId);
        LocalTime now = OperatingHours.currentTime(operatingHoursZone);
        List<SlotResponse> slotResponses = slots.findAllByMachineIdOrderBySlotNumberAsc(machineId)
            .stream().map(slot -> slotService.toAvailabilityResponse(machine, slot, now)).toList();

        boolean withinHours = OperatingHours.includes(
            machine.getOperatingHoursStart(), machine.getOperatingHoursEnd(), now);
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
