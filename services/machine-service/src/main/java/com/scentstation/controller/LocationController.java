package com.scentstation.controller;

import com.scentstation.dto.request.LocationRequest;
import com.scentstation.dto.response.LocationResponse;
import com.scentstation.service.LocationService;
import jakarta.validation.Valid;
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
