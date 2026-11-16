package com.scentstation.identity.controller;

import com.scentstation.identity.config.JwtAuthenticationFilter;
import com.scentstation.identity.config.SecurityConfig;
import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.rbac.AccessPolicy;
import com.scentstation.identity.repository.UserRepository;
import com.scentstation.identity.service.JwtService;
import com.scentstation.identity.tenant.Tenant;
import com.scentstation.identity.tenant.TenantRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = InternalIntrospectionController.class,
    properties = "security.internal.service-key=private-key")
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AccessPolicy.class})
class InternalIntrospectionSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockBean JwtService jwtService;
    @MockBean UserRepository users;
    @MockBean TenantRepository tenants;

    @BeforeEach
    void setup() {
        token("valid-token", user("valid", false, false, null));
        User stale = user("stale", false, false, null);
        stale.revokeSessions();
        token("stale-token", stale);
        token("locked-token", user("locked", true, false, null));
        token("suspended-token", user("suspended", false, false, 9L));
        Tenant suspended = new Tenant("suspended", "Suspended");
        suspended.setStatus(Tenant.Status.SUSPENDED);
        when(tenants.findById(9L)).thenReturn(Optional.of(suspended));
    }

    @Test
    void validBearerAndServiceKeyReturnFreshPrincipal() throws Exception {
        mockMvc.perform(post("/internal/auth/introspect")
                .header("Authorization", "Bearer valid-token")
                .header("X-Internal-Service-Key", "private-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.username").value("valid"))
            .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
    }

    @Test
    void missingOrWrongServiceKeyIsForbidden() throws Exception {
        mockMvc.perform(post("/internal/auth/introspect")
                .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/auth/introspect")
                .header("Authorization", "Bearer valid-token")
                .header("X-Internal-Service-Key", "wrong"))
            .andExpect(status().isForbidden());
    }

    @Test
    void missingBearerIsUnauthorized() throws Exception {
        mockMvc.perform(post("/internal/auth/introspect")
                .header("X-Internal-Service-Key", "private-key"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void staleLockedAndSuspendedActorsAreUnauthorized() throws Exception {
        for (String token : new String[] {"stale-token", "locked-token", "suspended-token"}) {
            mockMvc.perform(post("/internal/auth/introspect")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Internal-Service-Key", "private-key"))
                .andExpect(status().isUnauthorized());
        }
    }

    private void token(String token, User user) {
        when(jwtService.extractUsername(token)).thenReturn(user.getUsername());
        when(jwtService.extractTokenVersion(token)).thenReturn(0L);
        when(users.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
    }

    private User user(String username, boolean locked, boolean inactive, Long tenantId) {
        User user = new User(username, username + "@example.test", "hash", username, Role.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setTenantId(tenantId);
        if (locked) user.setLocked(true);
        if (inactive) user.setActive(false);
        return user;
    }
}
