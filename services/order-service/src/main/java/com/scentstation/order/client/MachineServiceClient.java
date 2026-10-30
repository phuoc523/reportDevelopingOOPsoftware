package com.scentstation.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MachineServiceClient implements MachineServiceGateway {

    private static final String COMMANDS_PATH = "/api/machines/dispense-commands";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final boolean simulationEnabled;
    private final String simulatedResult;
    private final ConcurrentMap<String, MachineCommandResponse> simulatedResults = new ConcurrentHashMap<>();

    public MachineServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${machine-service.base-url:http://localhost:8084}") String baseUrl,
            @Value("${machine-service.simulation-enabled:true}") boolean simulationEnabled,
            @Value("${machine-service.simulated-result:DISPENSED}") String simulatedResult
    ) {
        this.simulationEnabled = simulationEnabled;
        this.simulatedResult = simulatedResult;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public MachineCommandResponse submit(MachineCommandRequest request) {
        if (simulationEnabled) {
            simulatedResults.putIfAbsent(request.commandId(), new MachineCommandResponse(
                    request.orderId(),
                    request.commandId(),
                    simulatedResult,
                    "Simulated Machine Service result"
            ));
            return new MachineCommandResponse(
                    request.orderId(),
                    request.commandId(),
                    "ACCEPTED",
                    "Dispensing command accepted by simulated Machine Service"
            );
        }
        return restClient.post()
                .uri(COMMANDS_PATH)
                .body(request)
                .retrieve()
                .body(MachineCommandResponse.class);
    }

    @Override
    public MachineCommandResponse getResult(String commandId) {
        if (simulationEnabled) {
            return simulatedResults.get(commandId);
        }
        return restClient.get()
                .uri(COMMANDS_PATH + "/{commandId}", commandId)
                .retrieve()
                .body(MachineCommandResponse.class);
    }
}
