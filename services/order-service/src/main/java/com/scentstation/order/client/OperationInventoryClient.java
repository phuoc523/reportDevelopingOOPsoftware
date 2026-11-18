package com.scentstation.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class OperationInventoryClient implements OperationInventoryGateway {
    private final RestClient client;
    private final String serviceKey;

    public OperationInventoryClient(
            RestClient.Builder builder,
            @Value("${operation-service.base-url:http://localhost:8085}") String baseUrl,
            @Value("${security.internal.service-key:}") String serviceKey
    ) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.serviceKey = serviceKey;
    }

    @Override
    public void recordSuccessfulTrial(InstalledDispenseRequest request) {
        client.post()
                .uri("/api/inventory/consumptions/installed")
                .header("X-Internal-Service-Key", serviceKey)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
