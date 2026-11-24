package com.scentstation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scentstation.entity.MachineCommand;
import com.scentstation.entity.MachineCommandStatus;
import com.scentstation.entity.MachineSlot;
import com.scentstation.repository.MachineCommandRepository;
import com.scentstation.repository.MachineSlotRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MachineMqttGatewayTest {
    @Test
    void retriesAcceptedCommandWithSameCommandIdAndQosOne() throws Exception {
        MqttAsyncClient client = mock(MqttAsyncClient.class);
        MachineCommandRepository commands = mock(MachineCommandRepository.class);
        MachineSlotRepository slots = mock(MachineSlotRepository.class);
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        MachineMqttGateway gateway = new MachineMqttGateway(client, new MqttConnectOptions(),
            mock(MachineMqttMessageHandler.class), json, commands, slots);
        Instant issuedAt = Instant.parse("2026-11-24T12:00:00Z");
        MachineCommand command = mock(MachineCommand.class);
        when(command.getCommandId()).thenReturn("command-retry-1");
        when(command.getMachineId()).thenReturn(9101L);
        when(command.getSlotId()).thenReturn("91001");
        when(command.getIssuedAt()).thenReturn(issuedAt);
        when(command.getExpiresAt()).thenReturn(Instant.now().plusSeconds(60));
        MachineSlot slot = mock(MachineSlot.class);
        when(slot.getCalibratedSprayDurationMs()).thenReturn(420);
        when(client.isConnected()).thenReturn(true);
        when(commands.findTop100ByStatusAndExpiresAtAfterOrderByIssuedAtAsc(
            eq(MachineCommandStatus.ACCEPTED), org.mockito.ArgumentMatchers.any(Instant.class)))
            .thenReturn(List.of(command));
        when(slots.findByIdAndMachineId(91001L, 9101L)).thenReturn(Optional.of(slot));

        gateway.retryAcceptedCommands();
        gateway.retryAcceptedCommands();

        ArgumentCaptor<MqttMessage> message = ArgumentCaptor.forClass(MqttMessage.class);
        verify(client, times(2)).publish(eq("scentstation/9101/command"), message.capture());
        assertThat(message.getAllValues()).allSatisfy(value -> {
            assertThat(value.getQos()).isEqualTo(1);
            assertThat(value.isRetained()).isFalse();
        });
        var envelope = json.readTree(message.getAllValues().getFirst().getPayload());
        var retryEnvelope = json.readTree(message.getAllValues().getLast().getPayload());
        assertThat(envelope.path("payload").path("commandId").asText())
            .isEqualTo("command-retry-1");
        assertThat(envelope.path("payload").path("durationMs").asInt()).isEqualTo(420);
        assertThat(retryEnvelope.path("messageId").asText())
            .isEqualTo(envelope.path("messageId").asText());
    }
}
