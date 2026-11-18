package com.scentstation.operation.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.scentstation.operation.inventory.consumption.dto.request.RecordInstalledDispenseRequest;
import com.scentstation.operation.inventory.consumption.service.DispenseConsumptionService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispenseEventKafkaConsumerTest {
    @Mock
    private DispenseConsumptionService consumptions;

    @Test
    void consumesValidEventThroughIdempotentInventoryService() {
        DispenseEventKafkaConsumer consumer = new DispenseEventKafkaConsumer(consumptions);
        consumer.consume(new DispenseCompletedEvent(
            1, "dispense:cmd-1", "cmd-1", 1L, 101L, 201L,
            "CUSTOMER", "SUCCESS", LocalDateTime.now()));

        ArgumentCaptor<RecordInstalledDispenseRequest> request =
            ArgumentCaptor.forClass(RecordInstalledDispenseRequest.class);
        verify(consumptions).recordInstalledDispense(request.capture());
        assertEquals("cmd-1", request.getValue().getCommandId());
        assertEquals(1L, request.getValue().getTenantId());
        assertEquals(101L, request.getValue().getMachineId());
        assertEquals(201L, request.getValue().getSlotId());
    }

    @Test
    void rejectsUnsupportedOrIncompleteEvents() {
        DispenseEventKafkaConsumer consumer = new DispenseEventKafkaConsumer(consumptions);
        assertThrows(DispenseEventPayloadException.class, () -> consumer.consume(
            new DispenseCompletedEvent(2, "bad", "cmd", 1L, 1L, 1L,
                "CUSTOMER", "SUCCESS", LocalDateTime.now())));
    }
}
