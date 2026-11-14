package com.scentstation.notification.controller;

import com.scentstation.notification.dto.response.NotificationResponse;
import com.scentstation.notification.security.IdentityAuthorizationClient;
import com.scentstation.notification.security.NotificationAuthorizationException;
import com.scentstation.notification.security.NotificationAuthorizationResponse;
import com.scentstation.notification.service.NotificationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationApiController.class)
class NotificationApiControllerTests {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private NotificationService notifications;

    @MockBean
    private IdentityAuthorizationClient identity;

    @Test
    void createsWithTenantAndRecipientsReturnedByIdentity() throws Exception {
        when(identity.authorize(
            eq("Bearer user-jwt"), isNull(), isNull(), eq("NOTIFICATION_CONFIGURE"),
            eq("7"), eq(List.of("999"))
        )).thenReturn(new NotificationAuthorizationResponse("USER", 42L, "7", List.of("42")));
        when(notifications.create(any())).thenReturn(notification(12L, "7"));

        mvc.perform(post("/api/notifications")
                .header("Authorization", "Bearer user-jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tenantId":"7","title":"Alert","content":"Machine alert",
                     "notificationType":"ALERT","priority":"HIGH","source":"machine-service",
                     "recipientIds":["999"]}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(12))
            .andExpect(jsonPath("$.tenantId").value("7"))
            .andExpect(jsonPath("$.recipients").doesNotExist());

        verify(notifications).create(argThat(command ->
            "7".equals(command.tenantId()) && command.recipientIds().equals(List.of("42"))
        ));
    }

    @Test
    void rejectsInvalidCreateRequestBeforeAuthorization() throws Exception {
        mvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"","content":"","notificationType":"","priority":"",
                     "source":"","recipientIds":[]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors").exists());

        verifyNoInteractions(identity);
        verifyNoInteractions(notifications);
    }

    @Test
    void retrievesOnlyTheAuthenticatedUsersNotificationAndDoesNotExposeRecipientRows() throws Exception {
        when(identity.authorize(
            eq("Bearer user-jwt"), any(), any(), eq("NOTIFICATION_READ"), eq("7"), eq(List.of())
        )).thenReturn(new NotificationAuthorizationResponse("USER", 42L, "7", List.of()));
        when(notifications.getForRecipient(12L, "7", "42")).thenReturn(notification(12L, "7"));

        mvc.perform(get("/api/notifications/12")
                .param("tenantId", "7")
                .header("Authorization", "Bearer user-jwt"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(12))
            .andExpect(jsonPath("$.recipients").doesNotExist());

        verify(notifications).getForRecipient(12L, "7", "42");
    }

    @Test
    void listsAuthenticatedUsersInboxWithPaginationSortingAndReadStatus() throws Exception {
        when(identity.authorize(
            eq("Bearer user-jwt"), isNull(), isNull(), eq("NOTIFICATION_READ"),
            eq("7"), eq(List.of())
        )).thenReturn(new NotificationAuthorizationResponse("USER", 42L, "7", List.of()));
        when(notifications.listForRecipient(eq("7"), eq("42"), eq("ALERT"), eq("HIGH"), any()))
            .thenReturn(new PageImpl<>(
                List.of(notification(12L, "7", Instant.parse("2026-01-02T00:00:00Z"))),
                PageRequest.of(0, 10),
                1
            ));

        mvc.perform(get("/api/notifications")
                .param("tenantId", "7")
                .param("notificationType", "ALERT")
                .param("priority", "HIGH")
                .param("size", "10")
                .param("sortDirection", "asc")
                .header("Authorization", "Bearer user-jwt"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].readAt").value("2026-01-02T00:00:00Z"));

        verify(identity).authorize(
            eq("Bearer user-jwt"), isNull(), isNull(), eq("NOTIFICATION_READ"),
            eq("7"), eq(List.of())
        );
        verify(notifications).listForRecipient(
            eq("7"),
            eq("42"),
            eq("ALERT"),
            eq("HIGH"),
            argThat(pageable -> pageable.getSort().getOrderFor("notification.createdAt").isAscending())
        );
    }

    @Test
    void listsNotificationsForAuthorizedServicePrincipal() throws Exception {
        when(identity.authorize(
            isNull(), eq("machine-service"), eq("credential"), eq("NOTIFICATION_READ"),
            eq("7"), eq(List.of())
        )).thenReturn(new NotificationAuthorizationResponse("SERVICE", null, "7", List.of()));
        when(notifications.listByTenant(eq("7"), eq("ALERT"), eq("HIGH"), any()))
            .thenReturn(new PageImpl<>(List.of(notification(12L, "7")), PageRequest.of(0, 10), 1));

        mvc.perform(get("/api/notifications")
                .param("tenantId", "7")
                .param("notificationType", "ALERT")
                .param("priority", "HIGH")
                .header("X-Service-Principal-Id", "machine-service")
                .header("X-Service-Principal-Credential", "credential"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(12))
            .andExpect(jsonPath("$.items[0].readAt").value(nullValue()));
    }

    @Test
    void rejectsOutOfBoundsPagination() throws Exception {
        mvc.perform(get("/api/notifications").param("size", "101"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/notifications").param("page", "100001"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/notifications").param("sortDirection", "sideways"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(identity);
    }

    @Test
    void marksOnlyTheAuthenticatedUsersNotificationRead() throws Exception {
        Instant readAt = Instant.parse("2026-01-02T00:00:00Z");
        when(identity.authorize(
            eq("Bearer user-jwt"), any(), any(), eq("NOTIFICATION_READ"), eq("7"), eq(List.of())
        )).thenReturn(new NotificationAuthorizationResponse("USER", 42L, "7", List.of()));
        when(notifications.markAsReadForRecipient(12L, "7", "42"))
            .thenReturn(notification(12L, "7", readAt));

        mvc.perform(patch("/api/notifications/12/read")
                .param("tenantId", "7")
                .header("Authorization", "Bearer user-jwt"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(12))
            .andExpect(jsonPath("$.readAt").value(readAt.toString()))
            .andExpect(jsonPath("$.recipients").doesNotExist());

        verify(notifications).markAsReadForRecipient(12L, "7", "42");
    }

    @Test
    void marksBulkNotificationsReadAndReturnsChangedCount() throws Exception {
        when(identity.authorize(
            eq("Bearer user-jwt"), any(), any(), eq("NOTIFICATION_READ"), eq("7"), eq(List.of())
        )).thenReturn(new NotificationAuthorizationResponse("USER", 42L, "7", List.of()));
        when(notifications.markManyAsReadForRecipient("7", "42", List.of(12L, 13L))).thenReturn(1);

        mvc.perform(patch("/api/notifications/read")
                .param("tenantId", "7")
                .header("Authorization", "Bearer user-jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"notificationIds":[12,13]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedCount").value(1));

        verify(notifications).markManyAsReadForRecipient("7", "42", List.of(12L, 13L));
    }

    @Test
    void returnsUnreadCountForAuthenticatedUser() throws Exception {
        when(identity.authorize(
            eq("Bearer user-jwt"), any(), any(), eq("NOTIFICATION_READ"), eq("7"), eq(List.of())
        )).thenReturn(new NotificationAuthorizationResponse("USER", 42L, "7", List.of()));
        when(notifications.countUnreadForRecipient("7", "42")).thenReturn(3L);

        mvc.perform(get("/api/notifications/unread-count")
                .param("tenantId", "7")
                .header("Authorization", "Bearer user-jwt"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").value(3));

        verify(notifications).countUnreadForRecipient("7", "42");
    }

    @Test
    void doesNotAllowServicePrincipalsToChangeOrQueryRecipientReadState() throws Exception {
        when(identity.authorize(
            isNull(), eq("machine-service"), eq("credential"), eq("NOTIFICATION_READ"),
            eq("7"), eq(List.of())
        )).thenReturn(new NotificationAuthorizationResponse("SERVICE", null, "7", List.of()));

        mvc.perform(patch("/api/notifications/12/read")
                .param("tenantId", "7")
                .header("X-Service-Principal-Id", "machine-service")
                .header("X-Service-Principal-Credential", "credential"))
            .andExpect(status().isForbidden());

        mvc.perform(patch("/api/notifications/read")
                .param("tenantId", "7")
                .header("X-Service-Principal-Id", "machine-service")
                .header("X-Service-Principal-Credential", "credential")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"notificationIds":[12]}
                    """))
            .andExpect(status().isForbidden());

        mvc.perform(get("/api/notifications/unread-count")
                .param("tenantId", "7")
                .header("X-Service-Principal-Id", "machine-service")
                .header("X-Service-Principal-Credential", "credential"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(notifications);
    }

    @Test
    void rejectsEmptyBulkReadRequestBeforeAuthorization() throws Exception {
        mvc.perform(patch("/api/notifications/read")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"notificationIds":[]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors").exists());

        verifyNoInteractions(identity);
        verifyNoInteractions(notifications);
    }

    @Test
    void rejectsNonPositiveIdsInBulkReadRequestBeforeAuthorization() throws Exception {
        mvc.perform(patch("/api/notifications/read")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"notificationIds":[0,-1]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors").exists());

        verifyNoInteractions(identity);
        verifyNoInteractions(notifications);
    }

    @Test
    void reportsIdentityAuthorizationDenialWithoutCallingNotificationService() throws Exception {
        when(identity.authorize(
            nullable(String.class), nullable(String.class), nullable(String.class),
            nullable(String.class), nullable(String.class), nullable(List.class)
        ))
            .thenThrow(new NotificationAuthorizationException(HttpStatus.FORBIDDEN));

        mvc.perform(get("/api/notifications/12").header("Authorization", "Bearer invalid"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.message").value("Caller is not authorized for this notification operation"));

        verifyNoInteractions(notifications);
    }

    private NotificationResponse notification(Long id, String tenantId) {
        return notification(id, tenantId, null);
    }

    private NotificationResponse notification(Long id, String tenantId, Instant readAt) {
        return new NotificationResponse(
            id, tenantId, "Alert", "Machine alert", "ALERT", "HIGH", "machine-service", null,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:00Z"),
            List.of(new NotificationResponse.RecipientState(
                1L, "42", readAt, Instant.parse("2026-01-01T00:00:00Z")
            ))
        );
    }
}
