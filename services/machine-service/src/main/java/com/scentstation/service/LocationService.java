package com.scentstation.service;


import com.scentstation.dto.response.LocationResponse;
import com.scentstation.entity.Location;
import com.scentstation.exception.ConflictException;
import com.scentstation.exception.InvalidOperationException;
import com.scentstation.exception.ResourceNotFoundException;
import com.scentstation.repository.LocationRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class LocationService {
    private final LocationRepository locations;

    public LocationService(LocationRepository locations) {
        this.locations = locations;
    }

    @Transactional
    public LocationResponse create(@NonNull String locationId, String tenantId, String name, String address) {
        String normalizedLocationId = Objects.requireNonNull(locationId.trim(), "locationId");
        if (locations.existsById(normalizedLocationId)) {
            throw new ConflictException("locationId already exists");
        }
        return toResponse(locations.save(new Location(
            normalizedLocationId, tenantId.trim(), name.trim(), address
        )));
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> list(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new InvalidOperationException("tenantId is required");
        }
        return locations.findAllByTenantIdOrderByNameAsc(tenantId.trim()).stream()
            .map(LocationService::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public LocationResponse get(@NonNull String locationId) {
        return toResponse(requireLocation(locationId));
    }

    @Transactional
    public LocationResponse update(@NonNull String locationId, @NonNull String requestedLocationId,
                                   @NonNull String tenantId, String name, String address) {
        Location location = requireLocation(locationId);
        if (!locationId.equals(requestedLocationId)) {
            throw new InvalidOperationException("locationId cannot be changed");
        }
        if (!location.getTenantId().equals(tenantId.trim())) {
            throw new InvalidOperationException(
                "Location Tenant ownership cannot be changed; create a new location instead"
            );
        }
        location.setName(name.trim());
        location.setAddress(address);
        return toResponse(locations.save(location));
    }

    @Transactional
    public LocationResponse setActive(@NonNull String locationId, boolean active) {
        Location location = requireLocation(locationId);
        location.setActive(active);
        return toResponse(locations.save(location));
    }

    private Location requireLocation(@NonNull String locationId) {
        String requiredLocationId = Objects.requireNonNull(locationId, "locationId");
        return locations.findById(requiredLocationId).orElseThrow(() ->
            new ResourceNotFoundException("Location not found: " + locationId));
    }

    private static LocationResponse toResponse(Location location) {
        return new LocationResponse(
            location.getLocationId(),
            location.getTenantId(),
            location.getName(),
            location.getAddress(),
            location.isActive(),
            location.getCreatedAt(),
            location.getUpdatedAt()
        );
    }
}
