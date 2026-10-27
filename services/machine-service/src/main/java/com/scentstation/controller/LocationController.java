package com.scentstation.controller;

import com.scentstation.dto.request.LocationRequest;
import com.scentstation.dto.response.LocationResponse;
import com.scentstation.exception.InvalidOperationException;
import com.scentstation.service.LocationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
public class LocationController {
    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LocationResponse create(@Valid @RequestBody LocationRequest request) {
        return locationService.create(
            required(request.getLocationId(), "locationId"),
            required(request.getTenantId(), "tenantId"),
            required(request.getName(), "name"),
            request.getAddress()
        );
    }

    @GetMapping
    public List<LocationResponse> list(@RequestParam String tenantId) {
        return locationService.list(tenantId);
    }

    @GetMapping("/{locationId}")
    public LocationResponse get(@PathVariable @NonNull String locationId) {
        return locationService.get(locationId);
    }

    @PatchMapping("/{locationId}/active")
    public LocationResponse setActive(@PathVariable @NonNull String locationId,
                                      @RequestParam boolean active) {
        return locationService.setActive(locationId, active);
    }

    @PutMapping("/{locationId}")
    public LocationResponse update(@PathVariable @NonNull String locationId,
                                   @Valid @RequestBody LocationRequest request) {
        return locationService.update(
            locationId, required(request.getLocationId(), "locationId"),
            required(request.getTenantId(), "tenantId"),
            required(request.getName(), "name"), request.getAddress()
        );
    }

    @NonNull
    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidOperationException(field + " is required");
        }
        return value;
    }
}
