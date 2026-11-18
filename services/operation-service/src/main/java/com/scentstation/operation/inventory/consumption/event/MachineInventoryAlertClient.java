package com.scentstation.operation.inventory.consumption.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class MachineInventoryAlertClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MachineInventoryAlertClient.class);
    private final RestClient client;
    private final String serviceKey;

    public MachineInventoryAlertClient(
            RestClient.Builder builder,
            @Value("${machine-service.base-url:http://localhost:8084}") String baseUrl,
            @Value("${security.internal.service-key:}") String serviceKey
    ) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.serviceKey = serviceKey;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishLowStock(InventoryLowStockEvent event) {
        String summary = "Nước hoa sắp hết: chai " + event.bottleId()
                + " còn " + event.remainingQuantity().stripTrailingZeros().toPlainString()
                + " ml (khoảng " + event.estimatedRemainingSprays() + " lượt xịt).";
        try {
            client.post()
                    .uri("/api/alerts/integration/low-stock")
                    .header("X-Internal-Service-Key", serviceKey)
                    .body(new LowStockAlertRequest(
                            "inventory-dispense:" + event.commandId(),
                            event.tenantId().toString(), event.machineId(), event.slotId(), summary))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            LOGGER.error("Could not publish low-stock alert for command {}", event.commandId(), exception);
        }
    }

    private record LowStockAlertRequest(
            String eventId, String tenantId, Long machineId, Long slotId, String summary
    ) {
    }
}
