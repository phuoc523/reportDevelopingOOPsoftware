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
    private final LocationRepository locations;
    public LocationController(LocationRepository locations) { this.locations = locations; }
    public record LocationRequest(@NotBlank @Size(max=100) String locationId, @NotBlank @Size(max=100) String tenantId,
                                  @NotBlank @Size(max=150) String name, @Size(max=500) String address) {}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Location create(@Valid @RequestBody LocationRequest request) {
        if (locations.existsById(request.locationId().trim())) throw new ConflictException("locationId already exists");
        return locations.save(new Location(request.locationId().trim(), request.tenantId().trim(), request.name().trim(), request.address()));
    }
    @GetMapping public List<Location> list(@RequestParam String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new InvalidOperationException("tenantId is required");
        return locations.findAllByTenantIdOrderByNameAsc(tenantId.trim());
    }
    @PutMapping("/{locationId}") public Location update(@PathVariable String locationId, @Valid @RequestBody LocationRequest request) {
        Location location = locations.findById(locationId).orElseThrow(() -> new ResourceNotFoundException("Location not found: " + locationId));
        if (!locationId.equals(request.locationId())) throw new InvalidOperationException("locationId cannot be changed");
        if (!location.getTenantId().equals(request.tenantId().trim())) throw new InvalidOperationException("Location Tenant ownership cannot be changed; create a new location instead");
        location.setName(request.name().trim()); location.setAddress(request.address());
        return locations.save(location);
    }
}
