package com.scentstation.order.client;

import com.scentstation.order.dto.response.PaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ReportingTransactionClient {

    private static final Logger log = LoggerFactory.getLogger(ReportingTransactionClient.class);

    private final RestClient restClient;
    private final boolean enabled;
    private final String apiKey;

    public ReportingTransactionClient(
            RestClient.Builder restClientBuilder,
            @Value("${reporting-service.enabled:false}") boolean enabled,
            @Value("${reporting-service.base-url:http://localhost:8086}") String baseUrl,
            @Value("${reporting-service.api-key:local-reporting-sync-key}") String apiKey
    ) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public void publish(PaymentResponse payment) {
        if (!enabled) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(payment);
                }
            });
            return;
        }
        send(payment);
    }

    private void send(PaymentResponse payment) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("reference", payment.reference());
            body.put("orderId", String.valueOf(payment.orderId()));
            body.put("amount", payment.amount());
            body.put("currency", payment.currency());
            body.put("status", payment.status().name());
            body.put("createdAt", payment.createdAt());
            body.put("paidAt", payment.paidAt());
            restClient.post()
                    .uri("/api/reporting/internal/transactions/sync")
                    .header("X-Internal-Api-Key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("Reporting sync failed for payment {}: {}", payment.reference(), ex.getMessage());
        }
    }
}
