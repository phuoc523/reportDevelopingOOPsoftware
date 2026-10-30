package com.scentstation;

import com.scentstation.dto.request.DeviceMessageRequest;
import com.scentstation.entity.DeviceRegistrationStatus;
import com.scentstation.entity.Machine;
import com.scentstation.entity.MachineCommand;
import com.scentstation.entity.MachineCommandStatus;
import com.scentstation.entity.MachineOperationalStatus;
import com.scentstation.exception.InvalidOperationException;
import com.scentstation.exception.ConflictException;
import com.scentstation.repository.MachineAlertRepository;
import com.scentstation.repository.MachineCommandRepository;
import com.scentstation.repository.MachineDeviceEventRepository;
import com.scentstation.repository.MachineTelemetryRepository;
import com.scentstation.service.DeviceMessageProcessor;
import com.scentstation.service.MachineCommandExpiryJob;
import com.scentstation.service.MachineHealthMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineIoTIntegrationTest extends MachineServiceIntegrationTestBase {
    @Autowired
    private DeviceMessageProcessor processor;

    @Autowired
    private MachineTelemetryRepository telemetryRepository;

    @Autowired
    private MachineDeviceEventRepository deviceEventRepository;

    @Autowired
    private MachineAlertRepository alertRepository;

    @Autowired
    private MachineCommandRepository commandRepository;

    @Autowired
    private MachineCommandExpiryJob commandExpiryJob;

    @Autowired
    private MachineHealthMonitor healthMonitor;

    @Test
    void persistsTelemetryAndIgnoresDuplicateEventIds() {
        Long machineId = createMachine(uniqueId("tenant"));
        Machine machine = machineRepository.findById(machineId).orElseThrow();
        DeviceMessageRequest message = deviceMessage(machine, "telemetry-1");
        message.setPerfumeLevelPercent(new BigDecimal("80.00"));
        message.setDoorOpen(false);
        message.setLeakDetected(false);
        message.setSensorStatus("OK");
        message.setActuatorStatus("OK");
        message.setFirmwareVersion("2.4.1");
        message.setConfigurationVersion("config-7");

        processor.telemetry(machine.getDeviceIdentity(), message);
        processor.telemetry(machine.getDeviceIdentity(), message);

        assertEquals(1, telemetryRepository.count());
        assertEquals("2.4.1", machineRepository.findById(machineId).orElseThrow().getFirmwareVersion());
        assertEquals("ONLINE", machineRepository.findById(machineId).orElseThrow()
            .getOperationalStatus().name());

        ResponseEntity<Map<String, Object>> response =
            getJson("/api/machines/" + machineId + "/telemetry?tenantId=" + machine.getTenantId()
                + "&size=1&page=0");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, ((java.util.List<?>) requireBody(response).get("content")).size());
        assertEquals("OK", ((Map<?, ?>) ((java.util.List<?>) requireBody(response).get("content"))
            .get(0)).get("sensorStatus"));
        assertEquals(1, ((Number) requireBody(response).get("totalElements")).intValue());
    }

    @Test
    void rejectsWrongTenantAndRevokedDeviceMessages() {
        Long machineId = createMachine(uniqueId("tenant"));
        Machine machine = machineRepository.findById(machineId).orElseThrow();
        DeviceMessageRequest wrongTopic = deviceMessage(machine, "wrong-topic");
        assertThrows(InvalidOperationException.class,
            () -> processor.heartbeat(uniqueId("other-device"), wrongTopic));

        DeviceMessageRequest wrongTenant = deviceMessage(machine, "tenant-mismatch");
        wrongTenant.setTenantId(uniqueId("tenant"));

        assertThrows(InvalidOperationException.class,
            () -> processor.heartbeat(machine.getDeviceIdentity(), wrongTenant));

        machine.setDeviceRegistrationStatus(DeviceRegistrationStatus.REVOKED);
        machineRepository.saveAndFlush(machine);
        DeviceMessageRequest revoked = deviceMessage(machine, "revoked-message");
        assertThrows(InvalidOperationException.class,
            () -> processor.heartbeat(machine.getDeviceIdentity(), revoked));
    }

    @Test
    void rejectsTelemetryOutOfRangeAndSlotFromAnotherMachine() {
        Long firstMachineId = createMachine(uniqueId("tenant"));
        Long secondMachineId = createMachine(uniqueId("tenant"));
        Machine firstMachine = machineRepository.findById(firstMachineId).orElseThrow();
        Machine secondMachine = machineRepository.findById(secondMachineId).orElseThrow();
        ResponseEntity<Map<String, Object>> slot = postJson(
            "/api/machines/" + firstMachineId + "/slots",
            slotRequest(1, "SLOT-1", "product-1", "cartridge-1", true, 1000));
        Long slotId = ((Number) requireBody(slot).get("id")).longValue();

        DeviceMessageRequest outOfRange = deviceMessage(firstMachine, "invalid-level");
        outOfRange.setPerfumeLevelPercent(new BigDecimal("100.01"));
        assertThrows(InvalidOperationException.class,
            () -> processor.telemetry(firstMachine.getDeviceIdentity(), outOfRange));

        DeviceMessageRequest wrongSlot = deviceMessage(secondMachine, "wrong-slot");
        wrongSlot.setSlotId(slotId);
        assertThrows(InvalidOperationException.class,
            () -> processor.telemetry(secondMachine.getDeviceIdentity(), wrongSlot));
        assertEquals(0, telemetryRepository.count());
    }

    @Test
    void eventIdsCannotBeReusedAcrossDevices() {
        Long firstId = createMachine(uniqueId("tenant"));
        Long secondId = createMachine(uniqueId("tenant"));
        Machine first = machineRepository.findById(firstId).orElseThrow();
        Machine second = machineRepository.findById(secondId).orElseThrow();
        DeviceMessageRequest firstMessage = deviceMessage(first, "globally-unique-event");
        processor.telemetry(first.getDeviceIdentity(), firstMessage);

        DeviceMessageRequest secondMessage = deviceMessage(second, "globally-unique-event");
        assertThrows(ConflictException.class,
            () -> processor.telemetry(second.getDeviceIdentity(), secondMessage));
        assertEquals(1, telemetryRepository.count());
    }

    @Test
    void heartbeatEventIdsCannotBeReusedByAnotherDevice() {
        Long firstId = createMachine(uniqueId("tenant"));
        Long secondId = createMachine(uniqueId("tenant"));
        Machine first = machineRepository.findById(firstId).orElseThrow();
        Machine second = machineRepository.findById(secondId).orElseThrow();
        processor.heartbeat(first.getDeviceIdentity(), deviceMessage(first, "shared-heartbeat-id"));

        assertThrows(ConflictException.class,
            () -> processor.heartbeat(second.getDeviceIdentity(),
                deviceMessage(second, "shared-heartbeat-id")));
    }

    @Test
    void doesNotRefreshMachineForDuplicateHeartbeat() {
        Long machineId = createMachine(uniqueId("tenant"));
        Machine machine = machineRepository.findById(machineId).orElseThrow();
        DeviceMessageRequest heartbeat = deviceMessage(machine, "heartbeat-once");
        processor.heartbeat(machine.getDeviceIdentity(), heartbeat);

        Instant oldLastSeen = Instant.now().minus(Duration.ofMinutes(3));
        machine = machineRepository.findById(machineId).orElseThrow();
        machine.setLastSeenAt(oldLastSeen);
        machine.setOperationalStatus(MachineOperationalStatus.OFFLINE);
        machineRepository.saveAndFlush(machine);

        processor.heartbeat(machine.getDeviceIdentity(), heartbeat);

        Machine unchanged = machineRepository.findById(machineId).orElseThrow();
        assertTrue(unchanged.getLastSeenAt().isBefore(Instant.now().minus(Duration.ofMinutes(2))));
        assertEquals(MachineOperationalStatus.OFFLINE, unchanged.getOperationalStatus());
    }

    @Test
    void marksStaleMachineOfflineAndResolvesAlertAfterHeartbeat() {
        Long machineId = createMachine(uniqueId("tenant"));
        Machine machine = machineRepository.findById(machineId).orElseThrow();
        machine.setLastSeenAt(Instant.now().minus(Duration.ofMinutes(2)));
        machine.setOperationalStatus(MachineOperationalStatus.ONLINE);
        machineRepository.saveAndFlush(machine);

        healthMonitor.refreshMachineStatuses();
        assertEquals(MachineOperationalStatus.OFFLINE,
            machineRepository.findById(machineId).orElseThrow().getOperationalStatus());
        assertEquals(1, alertRepository.count());

        DeviceMessageRequest heartbeat = deviceMessage(machine, "restored-heartbeat");
        processor.heartbeat(machine.getDeviceIdentity(), heartbeat);

        assertEquals(MachineOperationalStatus.ONLINE,
            machineRepository.findById(machineId).orElseThrow().getOperationalStatus());
        assertEquals("RESOLVED", alertRepository.findAll().get(0).getStatus().name());
    }

    @Test
    void machineWithoutAHeartbeatIsNotReportedAsAnOfflineAlert() {
        Long machineId = createMachine(uniqueId("tenant"));

        healthMonitor.refreshMachineStatuses();

        assertEquals(MachineOperationalStatus.OFFLINE,
            machineRepository.findById(machineId).orElseThrow().getOperationalStatus());
        assertEquals(0, alertRepository.count());
    }

    @Test
    void transitionsToUnstableAndLeavesDisabledOrMaintenanceMachinesAlone() {
        Long unstableId = createMachine(uniqueId("tenant"));
        Machine unstable = machineRepository.findById(unstableId).orElseThrow();
        unstable.setOperationalStatus(MachineOperationalStatus.ONLINE);
        unstable.setLastSeenAt(Instant.now().minus(Duration.ofSeconds(45)));
        machineRepository.saveAndFlush(unstable);

        Long maintenanceId = createMachine(uniqueId("tenant"));
        Machine maintenance = machineRepository.findById(maintenanceId).orElseThrow();
        maintenance.setOperationalStatus(MachineOperationalStatus.MAINTENANCE);
        maintenance.setLastSeenAt(Instant.now().minus(Duration.ofMinutes(5)));
        machineRepository.saveAndFlush(maintenance);

        Long disabledId = createMachine(uniqueId("tenant"));
        Machine disabled = machineRepository.findById(disabledId).orElseThrow();
        disabled.setOperationalStatus(MachineOperationalStatus.DISABLED);
        disabled.setLastSeenAt(Instant.now().minus(Duration.ofMinutes(5)));
        machineRepository.saveAndFlush(disabled);

        healthMonitor.refreshMachineStatuses();

        assertEquals(MachineOperationalStatus.UNSTABLE,
            machineRepository.findById(unstableId).orElseThrow().getOperationalStatus());
        assertEquals(MachineOperationalStatus.MAINTENANCE,
            machineRepository.findById(maintenanceId).orElseThrow().getOperationalStatus());
        assertEquals(MachineOperationalStatus.DISABLED,
            machineRepository.findById(disabledId).orElseThrow().getOperationalStatus());
        assertEquals(0, alertRepository.count());
    }

    @Test
    void telemetryOpensAndClearsDoorAlert() {
        Long machineId = createMachine(uniqueId("tenant"));
        Machine machine = machineRepository.findById(machineId).orElseThrow();
        DeviceMessageRequest opened = deviceMessage(machine, "door-open-1");
        opened.setDoorOpen(true);
        processor.telemetry(machine.getDeviceIdentity(), opened);
        assertEquals("OPEN", alertRepository.findAll().get(0).getStatus().name());

        DeviceMessageRequest closed = deviceMessage(machine, "door-closed-1");
        closed.setDoorOpen(false);
        processor.telemetry(machine.getDeviceIdentity(), closed);

        assertEquals("RESOLVED", alertRepository.findAll().get(0).getStatus().name());
    }

    @Test
    void openMachineOrSlotAlertMakesAvailabilityUnavailable() {
        Long machineId = createMachine(uniqueId("tenant"));
        Machine machine = machineRepository.findById(machineId).orElseThrow();
        patchJson("/api/machines/" + machineId + "/operational-status", Map.of("status", "ONLINE"));
        ResponseEntity<Map<String, Object>> slot = postJson(
            "/api/machines/" + machineId + "/slots",
            slotRequest(1, "SLOT-1", "product-1", "cartridge-1", true, 1000));
        Long slotId = ((Number) requireBody(slot).get("id")).longValue();

        DeviceMessageRequest message = deviceMessage(machine, "door-open-slot");
        message.setSlotId(slotId);
        message.setDoorOpen(true);
        processor.telemetry(machine.getDeviceIdentity(), message);

        Map<String, Object> availability = requireBody(
            getJson("/api/machines/" + machineId + "/availability"));
        assertEquals(Boolean.TRUE, availability.get("machineAvailable"));
        Map<?, ?> slotAvailability = (Map<?, ?>) ((java.util.List<?>) availability.get("slots")).get(0);
        assertEquals(Boolean.FALSE, slotAvailability.get("available"));
        assertTrue(slotAvailability.get("availabilityReason").toString().contains("DOOR_OPEN"));
    }

    @Test
    void inventoryLowStockAlertIsNotClearedByHealthyDeviceTelemetry() {
        Long machineId = createMachine(uniqueId("tenant"));
        Machine machine = machineRepository.findById(machineId).orElseThrow();
        ResponseEntity<Map<String, Object>> slot = postJson(
            "/api/machines/" + machineId + "/slots",
            slotRequest(1, "SLOT-1", "product-1", "cartridge-1", true, 1000));
        Long slotId = ((Number) requireBody(slot).get("id")).longValue();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("eventId", "inventory-low-stock");
        request.put("tenantId", machine.getTenantId());
        request.put("machineId", machineId);
        request.put("slotId", slotId);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service-Key", "test-integration-secret");
        ResponseEntity<Map<String, Object>> lowStock = rest.exchange(
            "/api/alerts/integration/low-stock", HttpMethod.POST,
            new HttpEntity<>(request, headers), new org.springframework.core.ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.CREATED, lowStock.getStatusCode());

        DeviceMessageRequest healthy = deviceMessage(machine, "healthy-stock-reading");
        healthy.setSlotId(slotId);
        healthy.setPerfumeLevelPercent(new BigDecimal("80.00"));
        processor.telemetry(machine.getDeviceIdentity(), healthy);

        assertEquals(1, alertRepository.count());
        assertEquals("INVENTORY", alertRepository.findAll().get(0).getSource().name());
        assertEquals("OPEN", alertRepository.findAll().get(0).getStatus().name());

        DeviceMessageRequest lowReading = deviceMessage(machine, "device-low-stock-reading");
        lowReading.setSlotId(slotId);
        lowReading.setPerfumeLevelPercent(new BigDecimal("10.00"));
        processor.telemetry(machine.getDeviceIdentity(), lowReading);
        assertEquals(2, alertRepository.count());
        assertTrue(alertRepository.findAll().stream()
            .anyMatch(alert -> alert.getSource().name().equals("DEVICE")
                && alert.getStatus().name().equals("OPEN")));
        assertTrue(alertRepository.findAll().stream()
            .anyMatch(alert -> alert.getSource().name().equals("INVENTORY")
                && alert.getStatus().name().equals("OPEN")));
    }

    @Test
    void machineLevelSafetyAlertBlocksMachineAndEverySlot() {
        Long machineId = createMachine(uniqueId("tenant"));
        Machine machine = machineRepository.findById(machineId).orElseThrow();
        patchJson("/api/machines/" + machineId + "/operational-status", Map.of("status", "ONLINE"));
        postJson("/api/machines/" + machineId + "/slots",
            slotRequest(1, "SLOT-1", "product-1", "cartridge-1", true, 1000));

        DeviceMessageRequest leak = deviceMessage(machine, "machine-leak");
        leak.setLeakDetected(true);
        processor.telemetry(machine.getDeviceIdentity(), leak);

        Map<String, Object> availability = requireBody(
            getJson("/api/machines/" + machineId + "/availability"));
        assertEquals(Boolean.FALSE, availability.get("machineAvailable"));
        assertTrue(availability.get("reason").toString().contains("LEAK_DETECTED"));
        Map<?, ?> slot = (Map<?, ?>) ((java.util.List<?>) availability.get("slots")).get(0);
        assertEquals(Boolean.FALSE, slot.get("available"));
        assertTrue(slot.get("availabilityReason").toString().contains("LEAK_DETECTED"));
    }

    @Test
    void telemetryHistorySupportsDateFilteringAndPagination() {
        Long machineId = createMachine(uniqueId("tenant"));
        Machine machine = machineRepository.findById(machineId).orElseThrow();
        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) {
            DeviceMessageRequest reading = deviceMessage(machine, "paged-reading-" + i);
            reading.setOccurredAt(now.minusSeconds(3 - i));
            reading.setPerfumeLevelPercent(BigDecimal.valueOf(40 + i));
            processor.telemetry(machine.getDeviceIdentity(), reading);
        }

        String basePath = "/api/machines/" + machineId + "/telemetry?tenantId="
            + machine.getTenantId() + "&from=" + now.minusSeconds(5)
            + "&until=" + now + "&size=1";
        ResponseEntity<Map<String, Object>> firstPage = getJson(basePath + "&page=0");
        ResponseEntity<Map<String, Object>> secondPage = getJson(basePath + "&page=1");

        assertEquals(3, ((Number) requireBody(firstPage).get("totalElements")).intValue());
        assertEquals(Boolean.TRUE, requireBody(firstPage).get("hasNext"));
        assertEquals(0, ((Number) requireBody(firstPage).get("page")).intValue());
        assertEquals(1, ((Number) requireBody(secondPage).get("page")).intValue());
        assertEquals(1, ((java.util.List<?>) requireBody(secondPage).get("content")).size());
    }

    @Test
    void httpHeartbeatRequiresInternalKeyAndResolvesOfflineAlert() {
        Long machineId = createMachine(uniqueId("tenant"));
        Machine machine = machineRepository.findById(machineId).orElseThrow();
        machine.setOperationalStatus(MachineOperationalStatus.ONLINE);
        machine.setLastSeenAt(Instant.now().minus(Duration.ofMinutes(2)));
        machineRepository.saveAndFlush(machine);
        healthMonitor.refreshMachineStatuses();
        assertEquals(1, alertRepository.count());

        ResponseEntity<Map<String, Object>> unauthorized = rest.exchange(
            "/api/machines/" + machineId + "/heartbeat", HttpMethod.POST, HttpEntity.EMPTY,
            new org.springframework.core.ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorized.getStatusCode());

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service-Key", "test-integration-secret");
        ResponseEntity<Map<String, Object>> heartbeat = rest.exchange(
            "/api/machines/" + machineId + "/heartbeat", HttpMethod.POST,
            new HttpEntity<>(null, headers), new org.springframework.core.ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, heartbeat.getStatusCode());
        assertEquals("ONLINE", requireBody(heartbeat).get("operationalStatus"));
        assertEquals("RESOLVED", alertRepository.findAll().get(0).getStatus().name());
    }

    @Test
    void escalatesOverdueAlertAndAllowsAcknowledgement() {
        Long machineId = createMachine(uniqueId("tenant"));
        Machine machine = machineRepository.findById(machineId).orElseThrow();
        DeviceMessageRequest event = deviceMessage(machine, "dispense-failure-1");
        event.setEventType("DISPENSE_FAILED");
        event.setOccurredAt(Instant.now().minus(Duration.ofMinutes(6)));
        processor.deviceEvent(machine.getDeviceIdentity(), event);

        healthMonitor.refreshMachineStatuses();
        assertEquals("ESCALATED", alertRepository.findAll().get(0).getStatus().name());

        ResponseEntity<Map<String, Object>> acknowledged = postJson(
            "/api/alerts/" + alertRepository.findAll().get(0).getId()
                + "/acknowledge?tenantId=" + machine.getTenantId(),
            Map.of("actorId", "operator-2"));
        assertEquals(HttpStatus.OK, acknowledged.getStatusCode());
        assertEquals("ACKNOWLEDGED", requireBody(acknowledged).get("status"));
    }

    @Test
    void storesEventsOnceAndSupportsAlertLifecycle() {
        Long machineId = createMachine(uniqueId("tenant"));
        Machine machine = machineRepository.findById(machineId).orElseThrow();
        DeviceMessageRequest event = deviceMessage(machine, "leak-event");
        event.setEventType("LEAK_DETECTED");
        event.setDetails("Leak near the cartridge compartment");

        processor.deviceEvent(machine.getDeviceIdentity(), event);
        processor.deviceEvent(machine.getDeviceIdentity(), event);

        assertEquals(1, deviceEventRepository.count());
        assertEquals(1, alertRepository.count());
        ResponseEntity<Map<String, Object>> deviceEventResponse = getJson(
            "/api/machines/" + machineId + "/device-events?tenantId=" + machine.getTenantId()
                + "&size=1&page=0");
        assertEquals(HttpStatus.OK, deviceEventResponse.getStatusCode());
        assertEquals("LEAK_DETECTED", ((Map<?, ?>) ((java.util.List<?>) requireBody(deviceEventResponse)
            .get("content")).get(0)).get("eventType"));
        Map<String, Object> alert = requireListBody(
            getJsonList("/api/alerts?tenantId=" + machine.getTenantId() + "&status=OPEN")).get(0);
        Long alertId = ((Number) alert.get("id")).longValue();
        assertEquals("CRITICAL", alert.get("severity"));
        assertEquals("OPEN", alert.get("status"));
        assertEquals(HttpStatus.OK, getJson("/api/alerts/" + alertId
            + "?tenantId=" + machine.getTenantId()).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, getJson("/api/alerts/" + alertId
            + "?tenantId=" + uniqueId("other-tenant")).getStatusCode());

        ResponseEntity<Map<String, Object>> acknowledged = postJson(
            "/api/alerts/" + alertId + "/acknowledge?tenantId=" + machine.getTenantId(),
            Map.of("actorId", "operator-1"));
        assertEquals(HttpStatus.OK, acknowledged.getStatusCode());
        assertEquals("ACKNOWLEDGED", requireBody(acknowledged).get("status"));
        assertTrue(((Number) requireBody(acknowledged).get("responseTimeMillis")).longValue() >= 0);

        ResponseEntity<Map<String, Object>> assigned = postJson(
            "/api/alerts/" + alertId + "/assign?tenantId=" + machine.getTenantId(),
            Map.of("assigneeId", "technician-1"));
        assertEquals("ASSIGNED", requireBody(assigned).get("status"));

        ResponseEntity<Map<String, Object>> resolved = postJson(
            "/api/alerts/" + alertId + "/resolve?tenantId=" + machine.getTenantId(),
            Map.of("actorId", "operator-1", "notes", "Seal replaced"));
        assertEquals("RESOLVED", requireBody(resolved).get("status"));

        ResponseEntity<Map<String, Object>> reopened = postJson(
            "/api/alerts/" + alertId + "/reopen?tenantId=" + machine.getTenantId(),
            Map.of("notes", "Leak detected again"));
        assertEquals("OPEN", requireBody(reopened).get("status"));
        assertEquals(1, ((Number) requireBody(reopened).get("reopenCount")).intValue());
    }

    @Test
    void commandAcknowledgementAndResultUpdateCommandLifecycle() {
        Long machineId = createMachine(uniqueId("tenant"));
        Machine machine = machineRepository.findById(machineId).orElseThrow();
        Instant issuedAt = Instant.now().minusSeconds(10);
        commandRepository.saveAndFlush(new MachineCommand("spray-command-1", machine,
            "DISPENSE", issuedAt, Instant.now().plus(Duration.ofMinutes(1))));

        DeviceMessageRequest acknowledgement = deviceMessage(machine, "command-ack-1");
        acknowledgement.setCommandId("spray-command-1");
        acknowledgement.setEventType("COMMAND_ACK");
        processor.deviceEvent(machine.getDeviceIdentity(), acknowledgement);
        assertEquals(MachineCommandStatus.ACKNOWLEDGED,
            commandRepository.findAll().get(0).getStatus());

        DeviceMessageRequest result = deviceMessage(machine, "command-result-1");
        result.setCommandId("spray-command-1");
        result.setEventType("COMMAND_RESULT");
        result.setSuccess(true);
        result.setDetails("Dispense completed");
        processor.deviceEvent(machine.getDeviceIdentity(), result);

        MachineCommand command = commandRepository.findAll().get(0);
        assertEquals(MachineCommandStatus.SUCCEEDED, command.getStatus());
        assertEquals(Boolean.TRUE, command.getResultSuccess());
        assertEquals("Dispense completed", command.getResultDetails());
        assertEquals(2, deviceEventRepository.count());
    }

    @Test
    void expiresOverduePendingAndAcknowledgedCommands() {
        Long machineId = createMachine(uniqueId("tenant"));
        Machine machine = machineRepository.findById(machineId).orElseThrow();
        Instant issuedAt = Instant.now().minus(Duration.ofMinutes(2));
        Instant expiresAt = Instant.now().minus(Duration.ofMinutes(1));
        MachineCommand pending = new MachineCommand("pending-expired-command", machine,
            "DISPENSE", issuedAt, expiresAt);
        MachineCommand acknowledged = new MachineCommand("acknowledged-expired-command", machine,
            "DISPENSE", issuedAt, expiresAt);
        acknowledged.acknowledge(issuedAt.plusSeconds(1));
        MachineCommand completed = new MachineCommand("completed-command", machine,
            "DISPENSE", issuedAt, Instant.now().plus(Duration.ofMinutes(1)));
        completed.complete(true, "done", Instant.now());
        commandRepository.saveAllAndFlush(
            Objects.requireNonNull(java.util.List.of(pending, acknowledged, completed)));

        commandExpiryJob.expireOverdueCommands();

        assertEquals(MachineCommandStatus.EXPIRED,
            commandRepository.findAll().stream()
                .filter(command -> command.getCommandId().equals("pending-expired-command"))
                .findFirst().orElseThrow().getStatus());
        assertEquals(MachineCommandStatus.EXPIRED,
            commandRepository.findAll().stream()
                .filter(command -> command.getCommandId().equals("acknowledged-expired-command"))
                .findFirst().orElseThrow().getStatus());
        assertEquals(MachineCommandStatus.SUCCEEDED,
            commandRepository.findAll().stream()
                .filter(command -> command.getCommandId().equals("completed-command"))
                .findFirst().orElseThrow().getStatus());
    }

    @Test
    void lowStockIntegrationRequiresSharedSecretAndDeduplicatesActiveAlert() {
        Long machineId = createMachine(uniqueId("tenant"));
        Machine machine = machineRepository.findById(machineId).orElseThrow();
        ResponseEntity<Map<String, Object>> slot = postJson(
            "/api/machines/" + machineId + "/slots",
            slotRequest(1, "SLOT-1", "product-1", "cartridge-1", true, 1000));
        assertEquals(HttpStatus.CREATED, slot.getStatusCode());
        Long slotId = ((Number) requireBody(slot).get("id")).longValue();

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("eventId", "low-stock-event-1");
        request.put("tenantId", machine.getTenantId());
        request.put("machineId", machineId);
        request.put("slotId", slotId);
        request.put("summary", "Inventory stock below threshold");
        String path = "/api/alerts/integration/low-stock";

        ResponseEntity<Map<String, Object>> unauthorized = postJson(path, request);
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorized.getStatusCode());

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service-Key", "test-integration-secret");
        ResponseEntity<Map<String, Object>> first = rest.exchange(path, HttpMethod.POST,
            new HttpEntity<>(request, headers), new org.springframework.core.ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        Long firstAlertId = ((Number) requireBody(first).get("id")).longValue();

        request.put("eventId", "low-stock-event-2");
        ResponseEntity<Map<String, Object>> second = rest.exchange(path, HttpMethod.POST,
            new HttpEntity<>(request, headers), new org.springframework.core.ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.CREATED, second.getStatusCode());
        assertEquals(firstAlertId, ((Number) requireBody(second).get("id")).longValue());
        assertEquals(1, alertRepository.count());
    }

    private DeviceMessageRequest deviceMessage(Machine machine, String eventId) {
        DeviceMessageRequest request = new DeviceMessageRequest();
        request.setEventId(eventId);
        request.setDeviceIdentity(machine.getDeviceIdentity());
        request.setTenantId(machine.getTenantId());
        request.setOccurredAt(Instant.now());
        return request;
    }
}
