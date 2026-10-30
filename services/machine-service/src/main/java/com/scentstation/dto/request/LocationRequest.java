package com.scentstation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LocationRequest {
    @NotBlank
    @Size(max = 100)
    private String locationId;

    @NotBlank
    @Size(max = 100)
    private String tenantId;

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 500)
    private String address;

    public LocationRequest() {
    }

    public LocationRequest(String locationId, String tenantId, String name, String address) {
        this.locationId = locationId;
        this.tenantId = tenantId;
        this.name = name;
        this.address = address;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
