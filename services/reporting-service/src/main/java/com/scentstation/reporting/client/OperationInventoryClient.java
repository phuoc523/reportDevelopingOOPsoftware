package com.scentstation.reporting.client;

import com.scentstation.reporting.dto.integration.BottleSnapshot;
import com.scentstation.reporting.dto.integration.InventoryBatchSnapshot;
import com.scentstation.reporting.dto.integration.InventoryMovementSnapshot;
import com.scentstation.reporting.dto.integration.MaintenanceTicketSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Component
public class OperationInventoryClient {

    private static final ParameterizedTypeReference<List<BottleSnapshot>> BOTTLES =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<InventoryBatchSnapshot>> BATCHES =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<InventoryMovementSnapshot>> MOVEMENTS =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<MaintenanceTicketSnapshot>> TICKETS =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public OperationInventoryClient(
            RestClient.Builder restClientBuilder,
            @Value("${integration.operation.base-url}") String baseUrl) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory())
                .build();
    }

    public List<BottleSnapshot> listBottles(long tenantId) {
        return list("/api/inventory/bottles", tenantId, BOTTLES, bottle ->
                positive(bottle.id()) && Long.valueOf(tenantId).equals(bottle.tenantId())
                && positive(bottle.productId()) && hasText(bottle.status())
                && bottle.currentQuantity() != null && bottle.currentQuantity().signum() >= 0);
    }

    public List<InventoryBatchSnapshot> listBatches(long tenantId) {
        return list("/api/inventory/batches", tenantId, BATCHES, batch ->
                positive(batch.id()) && Long.valueOf(tenantId).equals(batch.tenantId())
                && positive(batch.productId()) && batch.receivedQuantity() != null
                && batch.receivedQuantity() > 0 && batch.expired() != null);
    }

    public List<InventoryMovementSnapshot> listMovements(long tenantId) {
        return list("/api/inventory/movements", tenantId, MOVEMENTS, movement -> {
            if (!positive(movement.id()) || !Long.valueOf(tenantId).equals(movement.tenantId())
                    || !hasText(movement.movementType()) || movement.createdAt() == null) {
                return false;
            }
            boolean consumption = "CUSTOMER_CONSUMPTION".equals(movement.movementType())
                    || "DIAGNOSTIC_CONSUMPTION".equals(movement.movementType());
            return !consumption || (hasText(movement.referenceId())
                    && movement.quantityBefore() != null && movement.quantityAfter() != null
                    && movement.quantityAfter().signum() >= 0
                    && movement.quantityBefore().compareTo(movement.quantityAfter()) > 0);
        });
    }

    public List<MaintenanceTicketSnapshot> listMaintenanceTickets(long tenantId) {
        return list("/api/maintenance/tickets", tenantId, TICKETS, ticket ->
                positive(ticket.id()) && Long.valueOf(tenantId).equals(ticket.tenantId())
                && hasText(ticket.status()) && ticket.critical() != null);
    }

    private <T> List<T> list(String path, long tenantId, ParameterizedTypeReference<List<T>> type,
            java.util.function.Predicate<T> valid) {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("Operation tenantId must be positive");
        }
        try {
            List<T> values = restClient.get().uri(builder -> builder.path(path)
                    .queryParam("tenantId", tenantId).build()).retrieve().body(type);
            if (values == null || values.stream().anyMatch(value -> value == null || !valid.test(value))) {
                throw new RestClientException("Operation Service returned incomplete or mismatched data");
            }
            return List.copyOf(values);
        } catch (RestClientException ex) {
            throw new UpstreamUnavailableException("operation-service", ex);
        }
    }

    private static boolean positive(Long value) { return value != null && value > 0; }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }

    private static JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }
}
