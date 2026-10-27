package com.scentstation.service;

import com.scentstation.dto.request.DeviceMessageRequest;
import com.scentstation.entity.DeviceEventType;
import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.Machine;
import com.scentstation.entity.MachineCommand;
import com.scentstation.entity.MachineAlertSeverity;
import com.scentstation.entity.MachineAlertType;
import com.scentstation.entity.MachineDeviceEvent;
import com.scentstation.entity.MachineOperationalStatus;
import com.scentstation.entity.MachineTelemetry;
import com.scentstation.entity.MachineMessageReceipt;
import com.scentstation.exception.ConflictException;
import com.scentstation.exception.InvalidOperationException;
import com.scentstation.exception.ResourceNotFoundException;
import com.scentstation.repository.MachineDeviceEventRepository;
import com.scentstation.repository.MachineCommandRepository;
import com.scentstation.repository.MachineRepository;
import com.scentstation.repository.MachineSlotRepository;
import com.scentstation.repository.MachineTelemetryRepository;
import com.scentstation.repository.MachineMessageReceiptRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

@Service
public class DeviceMessageProcessor {
    private final MachineRepository machines;
    private final MachineSlotRepository slots;
    private final MachineTelemetryRepository telemetry;
    private final MachineDeviceEventRepository deviceEvents;
    private final MachineCommandRepository commands;
    private final MachineMessageReceiptRepository receipts;
    private final MachineAlertService alerts;
    private final BigDecimal lowStockPercent;

    public DeviceMessageProcessor(MachineRepository machines, MachineSlotRepository slots,
                                  MachineTelemetryRepository telemetry,
                                  MachineDeviceEventRepository deviceEvents,
                                  MachineCommandRepository commands,
                                  MachineMessageReceiptRepository receipts,
                                  MachineAlertService alerts,
                                  @Value("${machine.alerts.low-stock-percent:15}") BigDecimal lowStockPercent) {
        if (lowStockPercent.compareTo(BigDecimal.ZERO) < 0
                || lowStockPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("machine.alerts.low-stock-percent must be between 0 and 100");
        }
        this.machines = machines;
        this.slots = slots;
        this.telemetry = telemetry;
        this.deviceEvents = deviceEvents;
        this.commands = commands;
        this.receipts = receipts;
        this.alerts = alerts;
        this.lowStockPercent = lowStockPercent;
    }

    @Transactional
    public void heartbeat(String topicIdentity, DeviceMessageRequest message) {
        Machine machine = authenticate(topicIdentity, message);
        if (registerReceipt(machine, requireText(message.getEventId(), "eventId", 100), "HEARTBEAT")) {
            return;
        }
        if (message.getEventId().equals(machine.getLastHeartbeatEventId())) {
            return;
        }
        ensureEventIdUnusedByTelemetry(message.getEventId());
        ensureEventIdUnusedByDeviceEvent(message.getEventId());
        Instant now = Instant.now();
        updateMachineSeen(machine, message, now);
        machine.setLastHeartbeatEventId(message.getEventId());
        machines.save(machine);
        alerts.resolveMachineOfflineAlerts(machine, now);
    }

    @Transactional
    public void telemetry(String topicIdentity, DeviceMessageRequest message) {
        Machine machine = authenticate(topicIdentity, message);
        if (registerReceipt(machine, requireText(message.getEventId(), "eventId", 100), "TELEMETRY")) {
            return;
        }
        ensureHeartbeatEventIdUnused(machine, message.getEventId());
        MachineTelemetry priorTelemetry = telemetry.findByEventId(message.getEventId()).orElse(null);
        if (priorTelemetry != null) {
            if (!priorTelemetry.getMachineId().equals(machine.getId())) {
                throw new ConflictException("eventId has already been used by another device");
            }
            return;
        }
        ensureEventIdUnusedByDeviceEvent(message.getEventId());
        Instant occurredAt = message.getOccurredAt();
        telemetry.save(new MachineTelemetry(message.getEventId(), machine,
            message.getCommandId(), message.getSlotId(),
            message.getPerfumeLevelPercent(), message.getPerfumeWeightGrams(),
            message.getDoorOpen(), message.getLeakDetected(), message.getActuatorStatus(),
            message.getSensorStatus(), message.getPowerStatus(), message.getFirmwareVersion(),
            message.getConfigurationVersion(), occurredAt));
        Instant now = Instant.now();
        updateMachineSeen(machine, message, now);
        machines.save(machine);
        applyTelemetryAlerts(machine, message, occurredAt);
    }

    @Transactional
    public void deviceEvent(String topicIdentity, DeviceMessageRequest message) {
        Machine machine = authenticate(topicIdentity, message);
        if (registerReceipt(machine, requireText(message.getEventId(), "eventId", 100), "EVENT")) {
            return;
        }
        ensureHeartbeatEventIdUnused(machine, message.getEventId());
        MachineDeviceEvent priorEvent = deviceEvents.findByEventId(message.getEventId()).orElse(null);
        if (priorEvent != null) {
            if (!priorEvent.getMachineId().equals(machine.getId())) {
                throw new ConflictException("eventId has already been used by another device");
            }
            return;
        }
        ensureEventIdUnusedByTelemetry(message.getEventId());
        DeviceEventType type = parseEventType(message.getEventType());
        Instant occurredAt = message.getOccurredAt();
        updateCommandLifecycle(machine, message, type, occurredAt);
        deviceEvents.save(new MachineDeviceEvent(message.getEventId(), machine,
            message.getCommandId(), type, message.getDetails(), message.getFirmwareVersion(),
            message.getConfigurationVersion(), occurredAt));

        Instant now = Instant.now();
        updateMachineSeen(machine, message, now);
        machines.save(machine);
        alerts.resolveMachineOfflineAlerts(machine, now);
        applyDeviceEventAlert(machine, message, type, occurredAt);
    }

    private void updateCommandLifecycle(Machine machine, DeviceMessageRequest message,
                                        DeviceEventType type, Instant occurredAt) {
        if (type != DeviceEventType.COMMAND_ACK && type != DeviceEventType.COMMAND_RESULT) {
            return;
        }
        if (message.getCommandId() == null || message.getCommandId().isBlank()) {
            throw new InvalidOperationException("commandId is required for command acknowledgements and results");
        }
        MachineCommand command = commands.findByCommandIdAndMachineIdForUpdate(
                message.getCommandId(), machine.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Command not found for this device"));
        if (!command.getTenantId().equals(machine.getTenantId())) {
            throw new InvalidOperationException("Command tenant does not match the device tenant");
        }
        if (type == DeviceEventType.COMMAND_ACK) {
            command.acknowledge(occurredAt);
        } else {
            if (message.getSuccess() == null) {
                throw new InvalidOperationException("success is required for a command result");
            }
            command.complete(message.getSuccess(), message.getDetails(), occurredAt);
        }
        commands.save(command);
    }

    @NonNull
    private Machine authenticate(String topicIdentity, DeviceMessageRequest message) {
        if (topicIdentity == null || topicIdentity.isBlank() || message == null) {
            throw new InvalidOperationException("MQTT topic or device message is invalid");
        }
        requireText(message.getEventId(), "eventId", 100);
        requireText(message.getDeviceIdentity(), "deviceIdentity", 150);
        requireText(message.getTenantId(), "tenantId", 100);
        if (!topicIdentity.equalsIgnoreCase(message.getDeviceIdentity().trim())) {
            throw new InvalidOperationException("Topic device identity does not match the message");
        }
        if (message.getOccurredAt() == null) {
            throw new InvalidOperationException("occurredAt is required");
        }
        if (message.getOccurredAt().isAfter(Instant.now().plusSeconds(300))) {
            throw new InvalidOperationException("occurredAt cannot be more than five minutes in the future");
        }
        if (message.getCommandId() != null && message.getCommandId().length() > 100) {
            throw new InvalidOperationException("commandId must not exceed 100 characters");
        }
        validateTelemetry(message);

        Machine machine = machines.findByDeviceIdentityForUpdate(topicIdentity.trim())
            .orElseThrow(() -> new ResourceNotFoundException("Device is not registered"));
        if (machine.getDeviceRegistrationStatus() != DeviceRegistrationStatus.ACTIVE) {
            throw new InvalidOperationException("Device registration is revoked");
        }
        if (!machine.getTenantId().equals(message.getTenantId().trim())) {
            throw new InvalidOperationException("Device tenant does not match its registered tenant");
        }
        if (message.getSlotId() != null
                && slots.findByIdAndMachineId(message.getSlotId(), machine.getId()).isEmpty()) {
            throw new InvalidOperationException("slotId does not belong to the device");
        }
        if (message.getFirmwareVersion() != null) {
            requireText(message.getFirmwareVersion(), "firmwareVersion", 100);
        }
        if (message.getConfigurationVersion() != null) {
            requireText(message.getConfigurationVersion(), "configurationVersion", 100);
        }
        return machine;
    }

    private void validateTelemetry(DeviceMessageRequest message) {
        BigDecimal level = message.getPerfumeLevelPercent();
        if (level != null && (level.compareTo(BigDecimal.ZERO) < 0
                || level.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new InvalidOperationException("perfumeLevelPercent must be between 0 and 100");
        }
        if (level != null && level.stripTrailingZeros().scale() > 2) {
            throw new InvalidOperationException("perfumeLevelPercent supports at most two decimal places");
        }
        BigDecimal weight = message.getPerfumeWeightGrams();
        if (weight != null && weight.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidOperationException("perfumeWeightGrams cannot be negative");
        }
        if (weight != null && (weight.compareTo(new BigDecimal("9999999.999")) > 0
                || weight.stripTrailingZeros().scale() > 3)) {
            throw new InvalidOperationException("perfumeWeightGrams supports up to 7 integer and 3 decimal places");
        }
        validateOptionalLength(message.getSensorStatus(), "sensorStatus", 30);
        validateOptionalLength(message.getActuatorStatus(), "actuatorStatus", 30);
        validateOptionalLength(message.getPowerStatus(), "powerStatus", 30);
        validateOptionalLength(message.getDetails(), "details", 500);
    }

    private void updateMachineSeen(Machine machine, DeviceMessageRequest message, Instant now) {
        machine.setLastSeenAt(now);
        if (machine.getOperationalStatus() == MachineOperationalStatus.OFFLINE
                || machine.getOperationalStatus() == MachineOperationalStatus.UNSTABLE) {
            machine.setOperationalStatus(MachineOperationalStatus.ONLINE);
        }
        if (message.getFirmwareVersion() != null) {
            machine.setFirmwareVersion(message.getFirmwareVersion().trim());
        }
        if (message.getConfigurationVersion() != null) {
            machine.setConfigurationVersion(message.getConfigurationVersion().trim());
        }
    }

    private void applyTelemetryAlerts(Machine machine, DeviceMessageRequest message, Instant occurredAt) {
        recordOrResolve(machine, MachineAlertType.DOOR_OPEN, message.getDoorOpen(),
            MachineAlertSeverity.MEDIUM, message.getSlotId(), "Machine door is open", occurredAt);
        recordOrResolve(machine, MachineAlertType.LEAK_DETECTED, message.getLeakDetected(),
            MachineAlertSeverity.CRITICAL, message.getSlotId(), "Leak detected by device telemetry", occurredAt);
        recordOrResolve(machine, MachineAlertType.SENSOR_FAILURE, isFailure(message.getSensorStatus()),
            MachineAlertSeverity.HIGH, message.getSlotId(), "Device sensor failure reported", occurredAt);
        recordOrResolve(machine, MachineAlertType.ACTUATOR_FAILURE, isFailure(message.getActuatorStatus()),
            MachineAlertSeverity.HIGH, message.getSlotId(), "Device actuator failure reported", occurredAt);
        if (message.getPerfumeLevelPercent() != null) {
            recordOrResolve(machine, MachineAlertType.LOW_STOCK,
                message.getPerfumeLevelPercent().compareTo(lowStockPercent) <= 0,
                MachineAlertSeverity.MEDIUM, message.getSlotId(),
                "Perfume stock is at or below " + lowStockPercent + "%", occurredAt);
        }
    }

    private void applyDeviceEventAlert(Machine machine, DeviceMessageRequest message,
                                       DeviceEventType eventType, Instant occurredAt) {
        switch (eventType) {
            case SENSOR_ERROR -> alerts.record(machine, MachineAlertType.SENSOR_FAILURE,
                MachineAlertSeverity.HIGH, message.getSlotId(), detailsOrDefault(message, "Device sensor failure"), occurredAt);
            case ACTUATOR_ERROR -> alerts.record(machine, MachineAlertType.ACTUATOR_FAILURE,
                MachineAlertSeverity.HIGH, message.getSlotId(), detailsOrDefault(message, "Device actuator failure"), occurredAt);
            case DISPENSE_FAILED -> alerts.record(machine, MachineAlertType.DISPENSE_FAILED,
                MachineAlertSeverity.HIGH, message.getSlotId(), detailsOrDefault(message, "Device reported dispense failure"), occurredAt);
            case SLOT_EMPTY -> alerts.record(machine, MachineAlertType.SLOT_EMPTY,
                MachineAlertSeverity.MEDIUM, message.getSlotId(), detailsOrDefault(message, "Device reported an empty slot"), occurredAt);
            case LOW_STOCK -> alerts.record(machine, MachineAlertType.LOW_STOCK,
                MachineAlertSeverity.MEDIUM, message.getSlotId(), detailsOrDefault(message, "Device reported low stock"), occurredAt);
            case DOOR_OPEN -> alerts.record(machine, MachineAlertType.DOOR_OPEN,
                MachineAlertSeverity.MEDIUM, message.getSlotId(), detailsOrDefault(message, "Machine door is open"), occurredAt);
            case LEAK_DETECTED -> alerts.record(machine, MachineAlertType.LEAK_DETECTED,
                MachineAlertSeverity.CRITICAL, message.getSlotId(), detailsOrDefault(message, "Device reported a leak"), occurredAt);
            case COMMAND_RESULT -> {
                if (Boolean.FALSE.equals(message.getSuccess())) {
                    alerts.record(machine, MachineAlertType.DISPENSE_FAILED,
                        MachineAlertSeverity.HIGH, message.getSlotId(),
                        detailsOrDefault(message, "Device command failed"), occurredAt);
                }
            }
            default -> {
                // Informational device events are stored without opening an alert.
            }
        }
    }

    private void recordOrResolve(Machine machine, MachineAlertType type, Boolean condition,
                                 MachineAlertSeverity severity, Long slotId, String summary,
                                 Instant occurredAt) {
        if (condition == null) {
            return;
        }
        if (condition) {
            alerts.record(machine, type, severity, slotId, summary, occurredAt);
        } else {
            alerts.resolveObservedAlert(machine, type, slotId, "Condition cleared by telemetry", occurredAt);
        }
    }

    private void ensureEventIdUnusedByDeviceEvent(String eventId) {
        if (deviceEvents.findByEventId(eventId).isPresent()) {
            throw new ConflictException("eventId has already been used for another device message");
        }
    }

    private boolean registerReceipt(@NonNull Machine machine, @NonNull String eventId,
                                   @NonNull String messageType) {
        MachineMessageReceipt prior = receipts.findById(eventId).orElse(null);
        if (prior != null) {
            if (prior.getMachineId().equals(machine.getId())
                    && prior.getMessageType().equals(messageType)) {
                return true;
            }
            throw new ConflictException("eventId has already been used by another device message");
        }
        receipts.saveAndFlush(new MachineMessageReceipt(eventId, machine.getId(), messageType));
        return false;
    }

    private void ensureHeartbeatEventIdUnused(Machine machine, String eventId) {
        if (eventId.equals(machine.getLastHeartbeatEventId())) {
            throw new ConflictException("eventId has already been used for a heartbeat");
        }
    }

    private void ensureEventIdUnusedByTelemetry(String eventId) {
        if (telemetry.findByEventId(eventId).isPresent()) {
            throw new ConflictException("eventId has already been used for another device message");
        }
    }

    private DeviceEventType parseEventType(String eventType) {
        requireText(eventType, "eventType", 40);
        try {
            return DeviceEventType.valueOf(eventType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidOperationException("Unsupported eventType: " + eventType);
        }
    }

    @NonNull
    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidOperationException(field + " is required");
        }
        if (value.trim().length() > maxLength) {
            throw new InvalidOperationException(field + " must not exceed " + maxLength + " characters");
        }
        return java.util.Objects.requireNonNull(value.trim(), field);
    }

    private void validateOptionalLength(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new InvalidOperationException(field + " must not exceed " + maxLength + " characters");
        }
    }

    private Boolean isFailure(String status) {
        if (status == null) {
            return null;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "ERROR", "FAILED", "FAILURE" -> true;
            default -> false;
        };
    }

    private String detailsOrDefault(DeviceMessageRequest message, String defaultSummary) {
        return message.getDetails() == null || message.getDetails().isBlank()
            ? defaultSummary : message.getDetails().trim();
    }
}
