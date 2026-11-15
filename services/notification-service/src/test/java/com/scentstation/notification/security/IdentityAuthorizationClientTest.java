package com.scentstation.notification.security;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class IdentityAuthorizationClientTest {
    private MockRestServiceServer server;
    private IdentityAuthorizationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new IdentityAuthorizationClient(builder, "http://identity", "internal-key");
    }

    @Test
    void authorizesUserWithInternalAndBearerCredentials() {
        server.expect(once(), requestTo("http://identity/internal/notifications/authorize-user"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Internal-Service-Key", "internal-key"))
            .andExpect(header("Authorization", "Bearer jwt"))
            .andExpect(content().json("""
                {"tenantId":"requested","permission":"NOTIFICATION_READ","recipientIds":["99"]}
                """))
            .andRespond(withSuccess("""
                {"callerType":"USER","userId":42,"tenantId":"actual","validatedRecipientIds":["42"]}
                """, MediaType.APPLICATION_JSON));

        var response = client.authorize(
            "Bearer jwt", null, null, "NOTIFICATION_READ", "requested", List.of("99"));

        assertThat(response.userId()).isEqualTo(42L);
        assertThat(response.tenantId()).isEqualTo("actual");
        assertThat(response.validatedRecipientIds()).containsExactly("42");
        server.verify();
    }

    @Test
    void authorizesServiceWithServiceCredentials() {
        server.expect(requestTo("http://identity/internal/notifications/authorize-service"))
            .andExpect(header("X-Service-Principal-Id", "machine-service"))
            .andExpect(header("X-Service-Principal-Credential", "credential"))
            .andRespond(withSuccess("""
                {"callerType":"SERVICE","userId":null,"tenantId":"7","validatedRecipientIds":[]}
                """, MediaType.APPLICATION_JSON));

        var response = client.authorize(null, "machine-service", "credential",
            "NOTIFICATION_CONFIGURE", "7", List.of());

        assertThat(response.callerType()).isEqualTo("SERVICE");
        assertThat(response.userId()).isNull();
        server.verify();
    }

    @Test
    void rejectsMissingCallerCredentialsBeforeCallingIdentity() {
        assertThatThrownBy(() -> client.authorize(
            null, null, null, "NOTIFICATION_READ", "7", List.of()))
            .isInstanceOf(NotificationAuthorizationException.class)
            .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);
        server.verify();
    }

    @Test
    void preservesIdentityAuthorizationDenialAndMapsInfrastructureFailure() {
        server.expect(requestTo("http://identity/internal/notifications/authorize-user"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.authorize(
            "Bearer denied", null, null, "NOTIFICATION_READ", "7", List.of()))
            .isInstanceOf(NotificationAuthorizationException.class)
            .extracting("status").isEqualTo(HttpStatus.FORBIDDEN);
        server.verify();

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new IdentityAuthorizationClient(builder, "http://identity", "internal-key");
        server.expect(requestTo("http://identity/internal/notifications/authorize-user"))
            .andRespond(withException(new IOException("offline")));
        assertThatThrownBy(() -> client.authorize(
            "Bearer jwt", null, null, "NOTIFICATION_READ", "7", List.of()))
            .isInstanceOf(NotificationAuthorizationUnavailableException.class);
        server.verify();
    }

    @Test
    void rejectsMalformedIdentityResponse() {
        server.expect(requestTo("http://identity/internal/notifications/authorize-user"))
            .andRespond(withSuccess("""
                {"callerType":"USER","userId":null,"tenantId":"7","validatedRecipientIds":[]}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.authorize(
            "Bearer jwt", null, null, "NOTIFICATION_READ", "7", List.of()))
            .isInstanceOf(NotificationAuthorizationUnavailableException.class);
    }

    @Test
    void resolvesRecipientsUsingNotificationServiceIdentity() {
        server.expect(requestTo("http://identity/internal/notifications/resolve-recipients"))
            .andExpect(header("X-Service-Principal-Id", "notification-service"))
            .andExpect(header("X-Service-Principal-Credential", "internal-key"))
            .andRespond(withSuccess("""
                {"callerType":"SERVICE","userId":null,"tenantId":"7",
                 "validatedRecipientIds":["42","43"]}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.resolveNotificationRecipients("7")).containsExactly("42", "43");
        server.verify();
    }
}
