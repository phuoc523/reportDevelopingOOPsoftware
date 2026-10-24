package com.scentstation;

import java.time.Instant;

public class LocationResponse {
    private final String locationId;
    private final String tenantId;
    private final String name;
    private final String address;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    public LocationResponse(String locationId, String tenantId, String name, String address,
                            boolean active, Instant createdAt, Instant updatedAt) {
        this.locationId = locationId;
        this.tenantId = tenantId;
        this.name = name;
        this.address = address;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getLocationId() {
        return locationId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
