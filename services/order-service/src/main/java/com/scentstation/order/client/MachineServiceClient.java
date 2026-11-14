package com.scentstation.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class MachineServiceClient implements MachineServiceGateway, MachineAuthorityGateway {

    private static final String COMMANDS_PATH = "/api/machines/dispense-commands";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final String serviceKey;

    public MachineServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${machine-service.base-url:http://localhost:8084}") String baseUrl,
            @Value("${security.internal.service-key:}") String serviceKey
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.serviceKey = serviceKey;
    }

    @Override
    public MachineCommandResponse submit(MachineCommandRequest request) {
        return restClient.post()
                .uri(COMMANDS_PATH)
                .header("X-Internal-Service-Key", serviceKey)
                .body(request)
                .retrieve()
                .body(MachineCommandResponse.class);
    }

    @Override
    public MachineCommandResponse getResult(String commandId) {
        return restClient.get()
                .uri(COMMANDS_PATH + "/{commandId}", commandId)
                .header("X-Internal-Service-Key", serviceKey)
                .retrieve()
                .body(MachineCommandResponse.class);
    }

    @Override
    public MachineSlotSnapshot getSlot(long tenantId, long machineId, long slotId) {
        try {
            MachineSlotSnapshot value = restClient.get()
                    .uri(uri -> uri.path("/internal/machines/{machineId}/slots/{slotId}")
                            .queryParam("tenantId", tenantId).build(machineId, slotId))
                    .header("X-Internal-Service-Key", serviceKey).retrieve().body(MachineSlotSnapshot.class);
            if (value == null || value.machineId() == null || value.slotId() == null || value.tenantId() == null
                    || value.productId() == null || value.machineAvailable() == null || value.slotAvailable() == null
                    || value.machineId() != machineId || value.slotId() != slotId
                    || !value.tenantId().equals(Long.toString(tenantId))) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_GATEWAY, "Machine returned malformed slot data");
            }
            return value;
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Machine or slot not found");
            if (ex.getStatusCode().value() == 409 || ex.getStatusCode().value() == 422) throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Machine tenant or slot validation failed");
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Machine authority unavailable");
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Machine authority unavailable");
        }
    }
}
