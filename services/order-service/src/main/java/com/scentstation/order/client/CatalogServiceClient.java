package com.scentstation.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class CatalogServiceClient implements CatalogServiceGateway {
    private final RestClient client;
    private final String key;

    public CatalogServiceClient(RestClient.Builder builder,
            @Value("${catalog-service.base-url:http://localhost:8082}") String baseUrl,
            @Value("${security.internal.service-key:}") String key) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.key = key;
    }

    public CatalogProductSnapshot getProduct(long tenantId, long productId) {
        try {
            CatalogProductSnapshot value = client.get().uri(uri -> uri.path("/internal/catalog/order-products/{id}")
                    .queryParam("tenantId", tenantId).build(productId))
                    .header("X-Internal-Service-Key", key).retrieve().body(CatalogProductSnapshot.class);
            if (value == null || value.productId() == null || value.tenantId() == null || value.name() == null
                    || value.available() == null || value.unitPrice() == null || value.unitPrice().signum() < 0
                    || value.productId() != productId || value.tenantId() != tenantId) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Catalog returned malformed product data");
            }
            return value;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog authority unavailable");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog authority unavailable");
        }
    }
}
