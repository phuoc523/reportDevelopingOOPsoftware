package com.scentstation.identity.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants", uniqueConstraints = {
    @UniqueConstraint(name = "uk_tenants_tenant_code", columnNames = "tenant_code")
})
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_code", nullable = false, unique = true, length = 80)
    private String tenantCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "brand_name", nullable = false, length = 120)
    private String brandName;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "configuration_json", columnDefinition = "TEXT")
    private String configurationJson = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tenant() {
    }

    public Tenant(String tenantCode, String name, String brandName) {
        this.tenantCode = requireText(tenantCode, "tenantCode");
        this.name = requireText(name, "name");
        this.brandName = requireText(brandName, "brandName");
        this.configurationJson = "{}";
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public void updateDetails(String name, String brandName, String configurationJson) {
        this.name = requireText(name, "name");
        this.brandName = requireText(brandName, "brandName");
        if (configurationJson != null && !configurationJson.isBlank()) {
            this.configurationJson = configurationJson;
        }
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getId() { return id; }
    public String getTenantCode() { return tenantCode; }
    public String getName() { return name; }
    public String getBrandName() { return brandName; }
    public boolean isActive() { return active; }
    public String getConfigurationJson() { return configurationJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @jakarta.persistence.PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}