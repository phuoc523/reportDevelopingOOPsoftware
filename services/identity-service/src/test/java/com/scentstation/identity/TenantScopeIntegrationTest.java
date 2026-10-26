package com.scentstation.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.identity.model.StaffUser;
import com.scentstation.identity.model.Tenant;
import com.scentstation.identity.rbac.RoleAssignmentAuditRepository;
import com.scentstation.identity.repository.AuthSessionRepository;
import com.scentstation.identity.repository.LoginAttemptRepository;
import com.scentstation.identity.repository.StaffUserRepository;
import com.scentstation.identity.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "TENANT_INTROSPECTION_KEY=tenant-test-service-secret-32-chars-minimum")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantScopeIntegrationTest {
    private static final String PASSWORD = "correct-horse-123";
    private static final String BASE = "/api/v1/tenant-scope";
    private static final String INTERNAL = "/api/v1/internal/tenant/introspect";
    private static final String SERVICE_KEY = "tenant-test-service-secret-32-chars-minimum";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired StaffUserRepository users;
    @Autowired TenantRepository tenants;
    @Autowired AuthSessionRepository sessions;
    @Autowired LoginAttemptRepository attempts;
    @Autowired RoleAssignmentAuditRepository audits;
    @Autowired PasswordEncoder encoder;

    private Tenant a;
    private Tenant b;
    private StaffUser aViewer;
    private StaffUser bViewer;

    @BeforeEach
    void setup() {
        sessions.deleteAll();
        audits.deleteAll();
        attempts.deleteAll();
        users.deleteAll();
        tenants.deleteAll();
        a = tenants.save(new Tenant("TENANT_SCOPE_A"));
        b = tenants.save(new Tenant("TENANT_SCOPE_B"));
        aViewer = user("tenant-a-viewer", a, "VIEWER");
        bViewer = user("tenant-b-viewer", b, "VIEWER");
        user("tenant-a-admin", a, "BRAND_ADMIN");
        user("tenant-b-admin", b, "BRAND_ADMIN");
        user("tenant-platform-admin", null, "PLATFORM_SUPER_ADMIN");
    }

    private StaffUser user(String username, Tenant tenant, String role) {
        return users.save(new StaffUser(username, encoder.encode(PASSWORD), tenant, Set.of(role)));
    }

    private String rawToken(String username) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("accessToken").asText();
    }

    private String bearer(String username) throws Exception {
        return "Bearer " + rawToken(username);
    }

    @Test
    void tenantIsTakenFromVerifiedSessionAndSpoofedQueryHeaderBodyAreDenied() throws Exception {
        mvc.perform(get(BASE + "/me")).andExpect(status().isUnauthorized());
        String admin = bearer("tenant-a-admin");
        mvc.perform(get(BASE + "/me").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(a.getId()));
        mvc.perform(get(BASE + "/me").header("Authorization", admin)
                        .header("X-Tenant-Id", b.getId()))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/me").header("Authorization", admin)
                        .param("tenantId", b.getId()))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/check").header("Authorization", admin)
                        .param("tenantId", a.getId()))
                .andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/check").header("Authorization", admin)
                        .param("tenantId", b.getId()))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/check").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("tenantId", b.getId()))))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/check").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("tenantId", a.getId()))))
                .andExpect(status().isNoContent());
        mvc.perform(post(BASE + "/check").header("Authorization", admin)
                        .header("X-Tenant-Id", b.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("tenantId", a.getId()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void scopedDatabaseReadsDoNotRevealCrossTenantAccount() throws Exception {
        String admin = bearer("tenant-a-admin");
        mvc.perform(get(BASE + "/users/" + aViewer.getId()).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(a.getId()))
                .andExpect(jsonPath("$.username").value("tenant-a-viewer"));
        mvc.perform(get(BASE + "/users/" + bViewer.getId()).header("Authorization", admin))
                .andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/users/" + aViewer.getId()).header("Authorization", admin)
                        .header("X-Tenant-Id", b.getId()))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/users/" + aViewer.getId())
                        .header("Authorization", bearer("tenant-a-viewer")))
                .andExpect(status().isForbidden()); // Viewer lacks USER_READ.
    }

    @Test
    void crossTenantMutationThroughExistingRbacIsRejectedBeforeWrite() throws Exception {
        String admin = bearer("tenant-a-admin");
        mvc.perform(put("/api/v1/rbac/users/" + bViewer.getId() + "/roles/TECHNICIAN")
                        .header("Authorization", admin))
                .andExpect(status().isForbidden());
        assertThat(users.findById(bViewer.getId()).orElseThrow().getRoles())
                .containsExactly("VIEWER");
        assertThat(audits.count()).isZero();
        mvc.perform(put("/api/v1/rbac/users/" + aViewer.getId() + "/roles/TECHNICIAN")
                        .header("Authorization", admin))
                .andExpect(status().isOk());
        assertThat(users.findById(aViewer.getId()).orElseThrow().getRoles())
                .contains("TECHNICIAN");
        assertThat(audits.count()).isEqualTo(1);
    }

    @Test
    void platformAdminIsExplicitlyAllowedToSelectAnotherTenant() throws Exception {
        String platform = bearer("tenant-platform-admin");
        mvc.perform(get(BASE + "/check").header("Authorization", platform)
                        .param("tenantId", b.getId()))
                .andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/users/" + bViewer.getId()).header("Authorization", platform)
                        .param("tenantId", b.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(b.getId()));
        mvc.perform(get(BASE + "/users/" + bViewer.getId()).header("Authorization", platform)
                        .param("tenantId", a.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void internalIntrospectionRequiresServiceSecretAndValidOpaqueSession() throws Exception {
        String token = rawToken("tenant-a-admin");
        String body = mapper.writeValueAsString(Map.of("accessToken", token));
        mvc.perform(post(INTERNAL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(INTERNAL).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-Internal-Service-Key", "incorrect-secret"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(INTERNAL).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-Internal-Service-Key", SERVICE_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(a.getId()))
                .andExpect(jsonPath("$.username").value("tenant-a-admin"))
                .andExpect(jsonPath("$.permissions").isArray());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(post(INTERNAL).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-Internal-Service-Key", SERVICE_KEY))
                .andExpect(status().isUnauthorized());
    }
}
