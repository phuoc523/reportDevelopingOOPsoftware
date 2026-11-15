package com.scentstation.notification.event;

import com.scentstation.notification.repository.NotificationRepository;
import com.scentstation.notification.repository.NotificationRecipientRepository;
import com.scentstation.notification.security.IdentityAuthorizationClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MachineAlertIntakeTests {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private NotificationRecipientRepository recipients;

    @MockBean
    private IdentityAuthorizationClient identity;

    @BeforeEach
    void cleanDatabase() {
        notifications.deleteAll();
        reset(identity);
    }

    @Test
    void acceptsValidEventAndTreatsRetryAsSuccessfulDuplicate() throws Exception {
        when(identity.resolveNotificationRecipients("7")).thenReturn(List.of("42"));

        mvc.perform(post("/internal/events/machine-alerts")
                .header("X-Internal-Service-Key", "test-internal-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validEvent()))
            .andExpect(status().isAccepted());

        mvc.perform(post("/internal/events/machine-alerts")
                .header("X-Internal-Service-Key", "test-internal-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validEvent()))
            .andExpect(status().isOk());

        assertEquals(1, notifications.count());
        assertEquals(1, recipients.countByNotification_TenantIdAndRecipientIdAndReadAtIsNull("7", "42"));
    }

    @Test
    void rejectsWrongInternalKeyBeforeProcessing() throws Exception {
        mvc.perform(post("/internal/events/machine-alerts")
                .header("X-Internal-Service-Key", "wrong")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validEvent()))
            .andExpect(status().isForbidden());

        verifyNoInteractions(identity);
        assertEquals(0, notifications.count());
    }

    @Test
    void rejectsUnsupportedContractVersionBeforeProcessing() throws Exception {
        mvc.perform(post("/internal/events/machine-alerts")
                .header("X-Internal-Service-Key", "test-internal-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validEvent().replace("\"eventVersion\":1", "\"eventVersion\":2")))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(identity);
    }

    private String validEvent() {
        return """
            {
              "eventVersion":1,
              "eventId":"event-1",
              "alertId":11,
              "tenantId":"7",
              "machineId":22,
              "locationId":"location-3",
              "alertType":"MACHINE_OFFLINE",
              "severity":"CRITICAL",
              "status":"OPEN",
              "occurredAt":"2026-11-15T00:00:00Z",
              "source":"machine-service"
            }
            """;
    }
}
