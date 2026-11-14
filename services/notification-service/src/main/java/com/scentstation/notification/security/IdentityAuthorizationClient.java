package com.scentstation.notification.security;

import com.scentstation.notification.dto.request.CreateNotificationRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class IdentityAuthorizationClient {
    private final RestClient restClient;
    private final String internalServiceKey;

    public IdentityAuthorizationClient(
            RestClient.Builder builder,
            @Value("${notification.identity.url:http://localhost:8081}") String identityUrl,
            @Value("${notification.identity.internal-service-key:}") String internalServiceKey
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
            if (response == null) {
                throw new NotificationAuthorizationUnavailableException();
            }
            boolean validUser = "USER".equals(response.callerType()) && response.userId() != null;
            boolean validService = "SERVICE".equals(response.callerType()) && response.userId() == null;
            if (response.tenantId() == null || response.tenantId().isBlank()
                    || (!validUser && !validService)) {
                throw new NotificationAuthorizationUnavailableException();
            }
            return response;
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
        }
    }
}
