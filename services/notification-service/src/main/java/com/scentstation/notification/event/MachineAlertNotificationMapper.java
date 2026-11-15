package com.scentstation.notification.event;

import com.scentstation.notification.dto.command.CreateNotificationCommand;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MachineAlertNotificationMapper {
    public CreateNotificationCommand map(MachineAlertEvent event, List<String> recipientIds) {
        String label = switch (event.alertType()) {
            case "MACHINE_OFFLINE" -> "Machine offline";
            case "SLOT_EMPTY" -> "Empty machine slot";
            case "SENSOR_FAILURE" -> "Sensor failure";
            case "ACTUATOR_FAILURE" -> "Actuator failure";
            case "DOOR_OPEN" -> "Machine door open";
            case "LEAK_DETECTED" -> "Leak detected";
            case "DISPENSE_FAILED" -> "Dispense failed";
            case "LOW_STOCK" -> "Low stock";
            default -> throw new IllegalArgumentException("Unsupported machine alert type");
        };
        String title = switch (event.status()) {
            case "RESOLVED" -> "Resolved: " + label;
            case "ESCALATED" -> "Escalated: " + label;
            default -> switch (event.severity()) {
                case "CRITICAL" -> "Critical: " + label;
                case "HIGH" -> "High priority: " + label;
                default -> "Machine alert: " + label;
            };
        };
        String content = "%s on machine %d at location %s is %s. Severity: %s. Alert ID: %d. Occurred at: %s."
            .formatted(label, event.machineId(), event.locationId(), event.status(), event.severity(),
                event.alertId(), event.occurredAt());
        return new CreateNotificationCommand(
            event.tenantId(), title, content, "MACHINE_ALERT", event.severity(), event.source(),
            event.eventId(), recipientIds);
    }
}
