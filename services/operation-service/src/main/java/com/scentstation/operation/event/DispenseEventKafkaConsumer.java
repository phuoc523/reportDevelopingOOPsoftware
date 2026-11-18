package com.scentstation.operation.event;

import com.scentstation.operation.inventory.consumption.dto.request.RecordInstalledDispenseRequest;
import com.scentstation.operation.inventory.consumption.enums.DispenseResult;
import com.scentstation.operation.inventory.consumption.enums.DispenseType;
import com.scentstation.operation.inventory.consumption.service.DispenseConsumptionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "operation.events.kafka.enabled", havingValue = "true")
public class DispenseEventKafkaConsumer {
    private final DispenseConsumptionService consumptions;

    public DispenseEventKafkaConsumer(DispenseConsumptionService consumptions) {
        this.consumptions = consumptions;
    }

    @KafkaListener(
        topics = "${operation.events.kafka.dispense-topic:dispense-events.v1}",
        groupId = "${operation.events.kafka.group-id:operation-dispense-v1}"
    )
    public void consume(DispenseCompletedEvent event) {
        validate(event);
        consumptions.recordInstalledDispense(RecordInstalledDispenseRequest.builder()
            .tenantId(event.tenantId())
            .commandId(event.commandId())
            .machineId(event.machineId())
            .slotId(event.slotId())
            .dispenseType(DispenseType.valueOf(event.dispenseType()))
            .result(DispenseResult.valueOf(event.result()))
            .build());
    }

    private void validate(DispenseCompletedEvent event) {
        if (event == null || event.eventVersion() != 1
                || blank(event.eventId()) || event.eventId().length() > 100
                || blank(event.commandId()) || event.commandId().length() > 36
                || event.tenantId() == null || event.tenantId() <= 0
                || event.machineId() == null || event.machineId() <= 0
                || event.slotId() == null || event.slotId() <= 0
                || !"CUSTOMER".equals(event.dispenseType())
                || !"SUCCESS".equals(event.result())
                || event.occurredAt() == null) {
            throw new DispenseEventPayloadException("Invalid dispense event payload");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
