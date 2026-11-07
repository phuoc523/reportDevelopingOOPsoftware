package com.scentstation.service;

import com.scentstation.dto.response.MachineResponses.MachineAvailabilityResponse;
import com.scentstation.dto.response.MachineResponses.SlotResponse;
import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.Machine;
import com.scentstation.entity.MachineAlert;
import com.scentstation.entity.MachineAlertStatus;
import com.scentstation.entity.MachineOperationalStatus;
import com.scentstation.repository.MachineAlertRepository;
import com.scentstation.repository.MachineSlotRepository;
import com.scentstation.exception.InvalidOperationException;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.EnumSet;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AvailabilityService {
    private final MachineService machineService;
    private final SlotService slotService;
    private final MachineSlotRepository slots;
    private final MachineAlertRepository alerts;
    private final ZoneId operatingHoursZone;

    public AvailabilityService(MachineService machineService, SlotService slotService,
                               MachineSlotRepository slots, MachineAlertRepository alerts,
                               @Value("${machine.operating-hours.zone:Asia/Ho_Chi_Minh}") String operatingHoursZone) {
        this.machineService = machineService;
        this.slotService = slotService;
        this.slots = slots;
        this.alerts = alerts;
        this.operatingHoursZone = ZoneId.of(operatingHoursZone);
    }

    public void requireAvailableForDispense(@NonNull Long machineId, @NonNull Long slotId) {
        MachineAvailabilityResponse availability = getAvailability(machineId);
        if (!availability.isMachineAvailable()) {
            throw new InvalidOperationException("Machine is not available: " + availability.getReason());
        }
        SlotResponse slot = availability.getSlots().stream()
            .filter(item -> item.getId().equals(slotId))
            .findFirst()
            .orElseThrow(() -> new com.scentstation.exception.ResourceNotFoundException("Slot not found on machine: " + slotId));
        if (!slot.isAvailable()) {
            throw new InvalidOperationException("Slot is not available: " + slot.getAvailabilityReason());
        }
    }

    @Transactional(readOnly = true)
    public MachineAvailabilityResponse getAvailability(@NonNull Long machineId) {
        Machine machine = machineService.requireMachine(machineId);
        LocalTime now = OperatingHours.currentTime(operatingHoursZone);
        List<MachineAlert> activeAlerts = alerts.findAllByMachineIdAndStatusInOrderByLastSeenAtDesc(
            machineId, EnumSet.of(MachineAlertStatus.OPEN, MachineAlertStatus.ACKNOWLEDGED,
                MachineAlertStatus.ASSIGNED, MachineAlertStatus.ESCALATED));
        String machineAlertReason = activeAlerts.stream()
            .filter(alert -> alert.getSlotId() == null)
            .findFirst()
            .map(this::alertReason)
            .orElse(null);
        Map<Long, String> slotAlertReasons = activeAlerts.stream()
            .filter(alert -> alert.getSlotId() != null)
            .collect(Collectors.toMap(alert -> alert.getSlotId(), alert -> alertReason(alert),
                (first, ignored) -> first));
        List<SlotResponse> slotResponses = slots.findAllByMachineIdOrderBySlotNumberAsc(machineId)
            .stream().map(slot -> slotService.toAvailabilityResponse(machine, slot, now,
                machineAlertReason != null ? machineAlertReason : slotAlertReasons.get(slot.getId())))
            .toList();

        boolean withinHours = OperatingHours.includes(
            machine.getOperatingHoursStart(), machine.getOperatingHoursEnd(), now);
        boolean machineAvailable = machine.getDeviceRegistrationStatus() == DeviceRegistrationStatus.ACTIVE
            && machine.getOperationalStatus() == MachineOperationalStatus.ONLINE && withinHours
            && machineAlertReason == null;
        String reason = machine.getDeviceRegistrationStatus() == DeviceRegistrationStatus.REVOKED
            ? "Device registration has been revoked"
            : machine.getOperationalStatus() != MachineOperationalStatus.ONLINE
                ? "Machine operational status is " + machine.getOperationalStatus()
                : machineAlertReason != null ? machineAlertReason
                    : !withinHours ? "Machine is outside operating hours" : "Machine is online and within operating hours";

        return new MachineAvailabilityResponse(machineId, machineAvailable,
            machine.getOperationalStatus(), reason, slotResponses);
    }

    private String alertReason(MachineAlert alert) {
        return "Active " + alert.getType() + " alert: " + alert.getSummary();
    }
}
