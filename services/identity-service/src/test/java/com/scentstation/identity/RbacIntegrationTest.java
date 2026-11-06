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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacIntegrationTest {
    private static final String PASSWORD = "correct-horse-123";
    private static final String RBAC = "/api/v1/rbac";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired StaffUserRepository users;
    @Autowired TenantRepository tenants;
    @Autowired AuthSessionRepository sessions;
    @Autowired LoginAttemptRepository attempts;
    @Autowired RoleAssignmentAuditRepository audits;
    @Autowired PasswordEncoder passwords;

    private Tenant brandA;
    private Tenant brandB;
    private StaffUser viewer;
    private StaffUser otherBrandViewer;
    private StaffUser brandAdmin;
    private StaffUser platformAdmin;

    @BeforeEach
    void setup() {
        sessions.deleteAll();
        audits.deleteAll();
        attempts.deleteAll();
        users.deleteAll();
        tenants.deleteAll();

        brandA = tenants.save(new Tenant("BRAND_A"));
        brandB = tenants.save(new Tenant("BRAND_B"));
        viewer = create("viewer", brandA, "VIEWER");
        otherBrandViewer = create("viewer-b", brandB, "VIEWER");
        brandAdmin = create("brand-admin", brandA, "BRAND_ADMIN");
        create("brand-admin-b", brandB, "BRAND_ADMIN");
        platformAdmin = create("platform", null, "PLATFORM_SUPER_ADMIN");
        create("technician", brandA, "TECHNICIAN");
        create("inventory", brandA, "INVENTORY_STAFF");
        create("operator", brandA, "OPERATIONS_MANAGER");
        users.save(new StaffUser("multi", passwords.encode(PASSWORD), brandA,
                Set.of("VIEWER", "TECHNICIAN")));
    }

    private StaffUser create(String username, Tenant tenant, String role) {
        return users.save(new StaffUser(username, passwords.encode(PASSWORD), tenant, Set.of(role)));
    }

    private String login(String username) throws Exception {
        String json = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(json).get("accessToken").asText();
    }

    @Test
    void anonymousDeniedAndViewerCannotReadRoleCatalogOrAssign() throws Exception {
        mvc.perform(get(RBAC + "/me")).andExpect(status().isUnauthorized());
        mvc.perform(put(RBAC + "/users/" + viewer.getId() + "/roles/TECHNICIAN"))
                .andExpect(status().isUnauthorized());

        String viewerToken = login("viewer");
        mvc.perform(get(RBAC + "/me").header("Authorization", viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("VIEWER")))
                .andExpect(jsonPath("$.permissions", hasItem("REPORT_READ")))
                .andExpect(jsonPath("$.permissions", not(hasItem("ROLE_ASSIGN"))));
        mvc.perform(get(RBAC + "/roles").header("Authorization", viewerToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(put(RBAC + "/users/" + viewer.getId() + "/roles/TECHNICIAN")
                        .header("Authorization", viewerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleAssignmentAuditedAndOldSessionsRevokedImmediately() throws Exception {
        String adminToken = login("brand-admin");
        String viewerOldToken = login("viewer");
        mvc.perform(put(RBAC + "/users/" + viewer.getId() + "/roles/TECHNICIAN")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("GRANT"))
                .andExpect(jsonPath("$.roles", hasItem("TECHNICIAN")))
                .andExpect(jsonPath("$.auditId").isNotEmpty());
        mvc.perform(get(RBAC + "/me").header("Authorization", viewerOldToken))
                .andExpect(status().isUnauthorized());

        String viewerFreshToken = login("viewer");
        mvc.perform(get(RBAC + "/check/MACHINE_DIAGNOSTIC_SPRAY")
                        .header("Authorization", viewerFreshToken))
                .andExpect(status().isNoContent());

        mvc.perform(delete(RBAC + "/users/" + viewer.getId() + "/roles/TECHNICIAN")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("REVOKE"));
        mvc.perform(get(RBAC + "/me").header("Authorization", viewerFreshToken))
                .andExpect(status().isUnauthorized());
        String viewerCurrentToken = login("viewer");
        mvc.perform(get(RBAC + "/check/MACHINE_DIAGNOSTIC_SPRAY")
                        .header("Authorization", viewerCurrentToken))
                .andExpect(status().isForbidden());

        assertThat(audits.count()).isEqualTo(2);
        mvc.perform(get(RBAC + "/audit").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(RBAC + "/audit").header("Authorization", login("brand-admin-b")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void crossTenantAssignmentAndSuperAdminEscalationDeniedToBrandAdmin() throws Exception {
        String adminToken = login("brand-admin");
        mvc.perform(put(RBAC + "/users/" + otherBrandViewer.getId() + "/roles/TECHNICIAN")
                        .header("Authorization", adminToken))
                .andExpect(status().isForbidden());
        mvc.perform(put(RBAC + "/users/" + viewer.getId() + "/roles/PLATFORM_SUPER_ADMIN")
                        .header("Authorization", adminToken))
                .andExpect(status().isForbidden());
        mvc.perform(delete(RBAC + "/users/" + platformAdmin.getId() + "/roles/PLATFORM_SUPER_ADMIN")
                        .header("Authorization", adminToken))
                .andExpect(status().isForbidden());
        assertThat(audits.count()).isZero();
        assertThat(users.findById(otherBrandViewer.getId()).orElseThrow().getRoles())
                .containsExactly("VIEWER");

        String superToken = login("platform");
        mvc.perform(put(RBAC + "/users/" + otherBrandViewer.getId() + "/roles/TECHNICIAN")
                        .header("Authorization", superToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles", hasItem("TECHNICIAN")));
        assertThat(audits.count()).isEqualTo(1);
    }

    @Test
    void sensitivePermissionsAreExplicitlyGuardedAndScopedByTenant() throws Exception {
        mvc.perform(get(RBAC + "/check/REFUND_APPROVE")
                        .header("Authorization", login("brand-admin")))
                .andExpect(status().isNoContent());
        mvc.perform(get(RBAC + "/check/MACHINE_CONFIGURE")
                        .header("Authorization", login("brand-admin")))
                .andExpect(status().isNoContent());
        mvc.perform(get(RBAC + "/check/INVENTORY_ADJUST")
                        .header("Authorization", login("inventory")))
                .andExpect(status().isNoContent());
        mvc.perform(get(RBAC + "/check/MACHINE_DIAGNOSTIC_SPRAY")
                        .header("Authorization", login("technician")))
                .andExpect(status().isNoContent());
        mvc.perform(get(RBAC + "/check/REFUND_APPROVE")
                        .header("Authorization", login("operator")))
                .andExpect(status().isForbidden());
        mvc.perform(get(RBAC + "/check/MACHINE_CONFIGURE")
                        .header("Authorization", login("technician")))
                .andExpect(status().isForbidden());
        mvc.perform(get(RBAC + "/check/INVENTORY_ADJUST")
                        .header("Authorization", login("viewer")))
                .andExpect(status().isForbidden());

        mvc.perform(get(RBAC + "/check/REFUND_APPROVE").param("tenantId", brandB.getId())
                        .header("Authorization", login("brand-admin")))
                .andExpect(status().isForbidden());
        mvc.perform(get(RBAC + "/check/REFUND_APPROVE").param("tenantId", brandB.getId())
                        .header("Authorization", login("platform")))
                .andExpect(status().isNoContent());
    }

    @Test
    void multiRoleHasUnionAndDuplicateChangesAreRejected() throws Exception {
        String multi = login("multi");
        mvc.perform(get(RBAC + "/check/MACHINE_DIAGNOSTIC_SPRAY")
                        .header("Authorization", multi)).andExpect(status().isNoContent());
        mvc.perform(get(RBAC + "/check/REPORT_READ")
                        .header("Authorization", multi)).andExpect(status().isNoContent());
        mvc.perform(get(RBAC + "/check/REFUND_APPROVE")
                        .header("Authorization", multi)).andExpect(status().isForbidden());
        String adminToken = login("brand-admin");
        mvc.perform(put(RBAC + "/users/" + viewer.getId() + "/roles/VIEWER")
                        .header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_ALREADY_ASSIGNED"));
        mvc.perform(delete(RBAC + "/users/" + viewer.getId() + "/roles/TECHNICIAN")
                        .header("Authorization", adminToken))
                .andExpect(status().isConflict());
        mvc.perform(delete(RBAC + "/users/" + brandAdmin.getId() + "/roles/BRAND_ADMIN")
                        .header("Authorization", adminToken))
                .andExpect(status().isConflict());
        assertThat(audits.count()).isZero();
    }
}
