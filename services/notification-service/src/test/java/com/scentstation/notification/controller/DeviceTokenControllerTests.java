package com.scentstation.notification.controller;

import com.scentstation.notification.dto.response.DeviceRegistrationResponse;
import com.scentstation.notification.entity.DevicePlatform;
import com.scentstation.notification.security.IdentityAuthorizationClient;
import com.scentstation.notification.security.NotificationAuthorizationResponse;
import com.scentstation.notification.service.FirebaseDeviceRegistrationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeviceTokenController.class)
class DeviceTokenControllerTests {
    @Autowired MockMvc mvc;
    @MockBean FirebaseDeviceRegistrationService registrations;
    @MockBean IdentityAuthorizationClient identity;

    @Test
    void registersUsingIdentityResolvedTenantAndUserWithoutReturningToken() throws Exception {
        when(identity.authorize(eq("Bearer jwt"), isNull(), isNull(), eq("NOTIFICATION_READ"),
            eq("requested-tenant"), eq(List.of())))
            .thenReturn(new NotificationAuthorizationResponse("USER", 42L, "actual-tenant", List.of()));
        when(registrations.register("actual-tenant", "42", "phone", "secret-token", DevicePlatform.ANDROID))
            .thenReturn(response(true));

        mvc.perform(post("/api/notification-device-tokens")
                .header("Authorization", "Bearer jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tenantId":"requested-tenant","deviceId":"phone",
                     "firebaseToken":"secret-token","platform":"ANDROID"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tenantId").value("actual-tenant"))
            .andExpect(jsonPath("$.userId").value("42"))
            .andExpect(jsonPath("$.firebaseToken").doesNotExist());
    }

    @Test
    void rejectsInvalidPayloadBeforeAuthorization() throws Exception {
        mvc.perform(post("/api/notification-device-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceId\":\"\",\"firebaseToken\":\"\",\"platform\":null}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(identity, registrations);
    }

    @Test
    void revokesOnlyDeviceOwnedByIdentityResolvedUser() throws Exception {
        when(identity.authorize(eq("Bearer jwt"), isNull(), isNull(), eq("NOTIFICATION_READ"),
            eq("7"), eq(List.of())))
            .thenReturn(new NotificationAuthorizationResponse("USER", 42L, "7", List.of()));
        when(registrations.revoke("7", "42", "phone")).thenReturn(response(false));

        mvc.perform(delete("/api/notification-device-tokens/phone")
                .param("tenantId", "7").header("Authorization", "Bearer jwt"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        verify(registrations).revoke("7", "42", "phone");
    }

    @Test
    void returnsCurrentUsersDeviceRegistrationState() throws Exception {
        when(identity.authorize(eq("Bearer jwt"), isNull(), isNull(), eq("NOTIFICATION_READ"),
            eq("7"), eq(List.of())))
            .thenReturn(new NotificationAuthorizationResponse("USER", 42L, "7", List.of()));
        when(registrations.get("7", "42", "phone")).thenReturn(response(true));

        mvc.perform(get("/api/notification-device-tokens/phone")
                .param("tenantId", "7").header("Authorization", "Bearer jwt"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));
        verify(registrations).get("7", "42", "phone");
    }

    @Test
    void rejectsServicePrincipalFromSelfServiceApi() throws Exception {
        when(identity.authorize(nullable(String.class), isNull(), isNull(), eq("NOTIFICATION_READ"),
            nullable(String.class), eq(List.of())))
            .thenReturn(new NotificationAuthorizationResponse("SERVICE", null, "7", List.of()));

        mvc.perform(delete("/api/notification-device-tokens/phone").param("tenantId", "7"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(registrations);
    }

    private DeviceRegistrationResponse response(boolean active) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new DeviceRegistrationResponse(1L, "actual-tenant", "42", "phone",
            DevicePlatform.ANDROID, active, now, now, now);
    }
}
