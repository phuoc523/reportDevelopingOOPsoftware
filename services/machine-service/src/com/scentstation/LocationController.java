package com.scentstation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
public class LocationController {
    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    public static class LocationRequest {
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LocationResponse create(@Valid @RequestBody LocationRequest request) {
        return locationService.create(
            request.getLocationId(), request.getTenantId(), request.getName(), request.getAddress()
        );
    }

    @GetMapping
    public List<LocationResponse> list(@RequestParam String tenantId) {
        return locationService.list(tenantId);
    }

    @GetMapping("/{locationId}")
    public LocationResponse get(@PathVariable String locationId) {
        return locationService.get(locationId);
    }

    @PatchMapping("/{locationId}/active")
    public LocationResponse setActive(@PathVariable String locationId, @RequestParam boolean active) {
        return locationService.setActive(locationId, active);
    }

    @PutMapping("/{locationId}")
    public LocationResponse update(@PathVariable String locationId,
                                   @Valid @RequestBody LocationRequest request) {
        return locationService.update(
            locationId, request.getLocationId(), request.getTenantId(),
            request.getName(), request.getAddress()
        );
    }
}
