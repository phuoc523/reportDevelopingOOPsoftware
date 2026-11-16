package com.scentstation.notification.security;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class IdentityAuthorizationClient {
    private final RestClient restClient;
    private final String internalServiceKey;

    @Autowired
    public IdentityAuthorizationClient(
            RestClient.Builder builder,
            @Value("${notification.identity.url:http://localhost:8081}") String identityUrl,
            @Value("${notification.identity.internal-service-key:}") String internalServiceKey,
            @Value("${notification.identity.connect-timeout:2s}") Duration connectTimeout,
            @Value("${notification.identity.read-timeout:5s}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = builder.requestFactory(requestFactory).baseUrl(identityUrl).build();
        this.internalServiceKey = internalServiceKey;
    }

    IdentityAuthorizationClient(
            RestClient.Builder builder,
            String identityUrl,
            String internalServiceKey
    ) {
        this.restClient = builder.baseUrl(identityUrl).build();
        this.internalServiceKey = internalServiceKey;
    }

    public NotificationAuthorizationResponse authorize(
            String authorization,
            String serviceId,
            String serviceCredential,
            String permission,
            String tenantId,
            List<String> recipientIds
    ) {
        if (internalServiceKey == null || internalServiceKey.isBlank()) {
            throw new NotificationAuthorizationUnavailableException();
        }
        boolean userCall = authorization != null && authorization.startsWith("Bearer ");
        if (!userCall && (serviceId == null || serviceId.isBlank()
                || serviceCredential == null || serviceCredential.isBlank())) {
            throw new NotificationAuthorizationException(HttpStatus.UNAUTHORIZED);
        }

        String path = userCall
            ? "/internal/notifications/authorize-user"
            : "/internal/notifications/authorize-service";
        try {
            RestClient.RequestBodySpec request = restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Internal-Service-Key", internalServiceKey);
            if (userCall) {
                request.header("Authorization", authorization);
            } else {
                request.header("X-Service-Principal-Id", serviceId)
                    .header("X-Service-Principal-Credential", serviceCredential);
            }
            NotificationAuthorizationResponse response = request
                .body(new NotificationAuthorizationRequest(tenantId, permission, recipientIds))
                .retrieve()
                .body(NotificationAuthorizationResponse.class);
            if (response == null) throw new NotificationAuthorizationUnavailableException();
            boolean validUser = "USER".equals(response.callerType())
                && response.userId() != null && response.userId() > 0;
            boolean validService = "SERVICE".equals(response.callerType()) && response.userId() == null;
            if (response.tenantId() == null || response.tenantId().isBlank()
                    || (!validUser && !validService)) {
                throw new NotificationAuthorizationUnavailableException();
            }
            return normalized(response);
        } catch (RestClientResponseException exception) {
            HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
            if (status != null && (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN
                    || status == HttpStatus.BAD_REQUEST || status == HttpStatus.NOT_FOUND
                    || status == HttpStatus.CONFLICT)) {
                throw new NotificationAuthorizationException(status);
            }
            throw new NotificationAuthorizationUnavailableException();
        } catch (ResourceAccessException exception) {
            throw new NotificationAuthorizationUnavailableException();
        } catch (RestClientException exception) {
            throw new NotificationAuthorizationUnavailableException();
        }
    }

    public List<String> resolveNotificationRecipients(String tenantId) {
        if (internalServiceKey == null || internalServiceKey.isBlank()) {
            throw new NotificationAuthorizationUnavailableException();
        }
        try {
            NotificationAuthorizationResponse response = restClient.post()
                .uri("/internal/notifications/resolve-recipients")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Internal-Service-Key", internalServiceKey)
                .header("X-Service-Principal-Id", "notification-service")
                .header("X-Service-Principal-Credential", internalServiceKey)
                .body(new NotificationAuthorizationRequest(
                    tenantId, "NOTIFICATION_READ", List.of()))
                .retrieve()
                .body(NotificationAuthorizationResponse.class);
            if (response == null || !"SERVICE".equals(response.callerType())
                    || response.userId() != null || response.tenantId() == null
                    || !tenantId.trim().equals(response.tenantId().trim())) {
                throw new NotificationAuthorizationUnavailableException();
            }
            return normalizedRecipients(response.validatedRecipientIds());
        } catch (RestClientException exception) {
            throw new NotificationAuthorizationUnavailableException();
        }
    }

    private NotificationAuthorizationResponse normalized(NotificationAuthorizationResponse response) {
        return new NotificationAuthorizationResponse(
            response.callerType(),
            response.userId(),
            response.tenantId().trim(),
            normalizedRecipients(response.validatedRecipientIds())
        );
    }

    private List<String> normalizedRecipients(List<String> recipientIds) {
        if (recipientIds == null) throw new NotificationAuthorizationUnavailableException();
        Set<String> normalized = new LinkedHashSet<>();
        for (String recipientId : recipientIds) {
            if (recipientId == null || !recipientId.trim().matches("[1-9][0-9]*")) {
                throw new NotificationAuthorizationUnavailableException();
            }
            normalized.add(recipientId.trim());
        }
        return List.copyOf(normalized);
    }
}
