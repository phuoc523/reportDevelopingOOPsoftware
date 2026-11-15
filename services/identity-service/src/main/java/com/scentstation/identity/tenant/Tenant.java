package com.scentstation.identity.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "code", nullable = false, unique = true, length = 80)
    private String tenantCode;

    @Column(nullable = false)
    private boolean active = true;

    protected Tenant() {
    }

    public Tenant(String tenantCode) {
        this.id = UUID.randomUUID().toString();
        this.tenantCode = requireText(tenantCode, "tenantCode");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = requireText(tenantCode, "tenantCode");
    }

    public String getId() { return id; }
    public String getTenantCode() { return tenantCode; }
    public boolean isActive() { return active; }
}