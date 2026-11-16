package com.scentstation.reporting.client;

import com.scentstation.reporting.dto.integration.MachineAlertSnapshot;
import com.scentstation.reporting.dto.integration.MachineSnapshot;
import com.scentstation.reporting.dto.integration.LocationSnapshot;
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
public class MachineSnapshotClient {

    private static final ParameterizedTypeReference<List<MachineSnapshot>> MACHINES =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<MachineAlertSnapshot>> ALERTS =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<LocationSnapshot>> LOCATIONS =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public MachineSnapshotClient(
            RestClient.Builder restClientBuilder,
            @Value("${integration.machine.base-url}") String baseUrl) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory())
                .build();
    }

    public List<MachineSnapshot> listMachines(String tenantId) {
        if (!hasText(tenantId)) {
            throw new IllegalArgumentException("tenantId is required for machines");
        }
        try {
            List<MachineSnapshot> machines = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/machines");
                        if (tenantId != null && !tenantId.isBlank()) {
                            uriBuilder.queryParam("tenantId", tenantId);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(MACHINES);
            return checked(machines, machine -> machine.id() != null && machine.id() > 0
                    && hasText(machine.tenantId()) && hasText(machine.operationalStatus())
                    && (!hasText(tenantId) || tenantId.equals(machine.tenantId())));
        } catch (RestClientException ex) {
            throw new UpstreamUnavailableException("machine-service", ex);
        }
    }

    public List<MachineAlertSnapshot> listAlerts(String tenantId) {
        try {
            List<MachineAlertSnapshot> alerts = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/alerts")
                            .queryParam("tenantId", tenantId)
                            .build())
                    .retrieve()
                    .body(ALERTS);
            return checked(alerts, alert -> alert.id() != null && alert.id() > 0 && hasText(alert.status()));
        } catch (RestClientException ex) {
            throw new UpstreamUnavailableException("machine-service", ex);
        }
    }

    public List<LocationSnapshot> listLocations(String tenantId) {
        if (!hasText(tenantId)) {
            throw new IllegalArgumentException("tenantId is required for locations");
        }
        try {
            List<LocationSnapshot> locations = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/locations")
                            .queryParam("tenantId", tenantId).build())
                    .retrieve().body(LOCATIONS);
            return checked(locations, location -> hasText(location.locationId())
                    && tenantId.equals(location.tenantId()) && hasText(location.name())
                    && location.active() != null);
        } catch (RestClientException ex) {
            throw new UpstreamUnavailableException("machine-service", ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> List<T> checked(List<T> values, java.util.function.Predicate<T> valid) {
        if (values == null || values.stream().anyMatch(value -> value == null || !valid.test(value))) {
            throw new RestClientException("Machine Service returned incomplete or mismatched data");
        }
        return List.copyOf(values);
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }
}
