package com.scentstation.service;

import com.scentstation.exception.InvalidOperationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OperationServiceCatalogValidator {
    private final RestClient restClient;
    private final boolean enabled;
    private final String serviceKey;

    public OperationServiceCatalogValidator(@Value("${machine.integration.operation-service-url:}") String baseUrl,
                                            @Value("${machine.integration.shared-secret:}") String serviceKey) {
        this.enabled = !baseUrl.isBlank();
        this.serviceKey = serviceKey;
        this.restClient = RestClient.builder().baseUrl(enabled ? baseUrl : "http://operation-service.invalid").build();
    }

    public void validateReferences(String productId, String cartridgeId, String tenantId) {
        if (!enabled) {
            return;
        }
        requireExists("/api/products/{id}?tenantId={tenantId}", productId, tenantId, "Product");
        requireExists("/api/cartridges/{id}?tenantId={tenantId}", cartridgeId, tenantId, "Cartridge");
    }

    private void requireExists(String path, String id, String tenantId, String label) {
        try {
            HttpStatusCode status = restClient.get().uri(path, id, tenantId)
                .header("X-Internal-Service-Key", serviceKey)
                .retrieve().toBodilessEntity().getStatusCode();
            if (!status.is2xxSuccessful()) {
                throw new InvalidOperationException(label + " does not exist: " + id);
            }
        } catch (RestClientException exception) {
            throw new InvalidOperationException(label + " does not exist or Operation Service is unavailable: " + id);
        }
    }
}
