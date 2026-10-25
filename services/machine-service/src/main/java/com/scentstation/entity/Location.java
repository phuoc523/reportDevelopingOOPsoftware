package com.scentstation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "machine_locations")
public class Location {
    @Id
    @Column(name = "location_id", nullable = false, length = 100)
    private String locationId;
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(length = 500)
    private String address;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    protected Location() {}
    public Location(String locationId, String tenantId, String name, String address) {
        this.locationId = locationId; this.tenantId = tenantId; this.name = name; this.address = address; this.active = true;
    }
    @PrePersist void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public String getLocationId() { return locationId; }
    public String getTenantId() { return tenantId; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getAddress() { return address; }
    public void setAddress(String value) { address = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
