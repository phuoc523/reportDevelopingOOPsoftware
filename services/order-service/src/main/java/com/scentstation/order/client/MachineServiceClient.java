package com.scentstation.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class MachineServiceClient implements MachineServiceGateway {

    private static final String COMMANDS_PATH = "/api/machines/dispense-commands";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;

    public MachineServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${machine-service.base-url:http://localhost:8084}") String baseUrl
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
    }

    @Override
    public MachineCommandResponse submit(MachineCommandRequest request) {
        return restClient.post()
                .uri(COMMANDS_PATH)
                .body(request)
                .retrieve()
                .body(MachineCommandResponse.class);
    }

    @Override
    public MachineCommandResponse getResult(String commandId) {
        return restClient.get()
                .uri(COMMANDS_PATH + "/{commandId}", commandId)
                .retrieve()
                .body(MachineCommandResponse.class);
    }
}
