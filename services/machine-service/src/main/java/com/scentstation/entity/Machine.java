package com.scentstation.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "machines", uniqueConstraints = {
    @UniqueConstraint(name = "uk_machine_serial_number", columnNames = "serial_number"),
    @UniqueConstraint(name = "uk_machine_device_identity", columnNames = "device_identity")
}, indexes = {
    @Index(name = "idx_machine_tenant", columnList = "tenant_id"),
    @Index(name = "idx_machine_location", columnList = "location_id")
})
public class Machine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "serial_number", nullable = false, length = 100)
    private String serialNumber;

    @Column(name = "device_identity", nullable = false, length = 150)
    private String deviceIdentity;

    // Tenant and location are owned by other services; these are external identifiers, not cross-service JPA relations.
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "location_id", nullable = false, length = 100)
    private String locationId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_registration_status", nullable = false, length = 20)
    private DeviceRegistrationStatus deviceRegistrationStatus = DeviceRegistrationStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status", nullable = false, length = 20)
    private MachineOperationalStatus operationalStatus = MachineOperationalStatus.OFFLINE;

    @Column(name = "operating_hours_start")
    private LocalTime operatingHoursStart;

    @Column(name = "operating_hours_end")
    private LocalTime operatingHoursEnd;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "maintenance_checklist_complete", nullable = false)
    private boolean maintenanceChecklistComplete = false;

    @Column(name = "post_maintenance_test_passed", nullable = false)
    private boolean postMaintenanceTestPassed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (deviceRegistrationStatus == null) deviceRegistrationStatus = DeviceRegistrationStatus.ACTIVE;
        if (operationalStatus == null) operationalStatus = MachineOperationalStatus.OFFLINE;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getDeviceIdentity() { return deviceIdentity; }
    public void setDeviceIdentity(String deviceIdentity) { this.deviceIdentity = deviceIdentity; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getLocationId() { return locationId; }
    public void setLocationId(String locationId) { this.locationId = locationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public DeviceRegistrationStatus getDeviceRegistrationStatus() { return deviceRegistrationStatus; }
    public void setDeviceRegistrationStatus(DeviceRegistrationStatus status) { this.deviceRegistrationStatus = status; }
    public MachineOperationalStatus getOperationalStatus() { return operationalStatus; }
    public void setOperationalStatus(MachineOperationalStatus status) { this.operationalStatus = status; }
    public LocalTime getOperatingHoursStart() { return operatingHoursStart; }
    public void setOperatingHoursStart(LocalTime value) { this.operatingHoursStart = value; }
    public LocalTime getOperatingHoursEnd() { return operatingHoursEnd; }
    public void setOperatingHoursEnd(LocalTime value) { this.operatingHoursEnd = value; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant value) { this.lastSeenAt = value; }
    public boolean isMaintenanceChecklistComplete() { return maintenanceChecklistComplete; }
    public void setMaintenanceChecklistComplete(boolean value) { this.maintenanceChecklistComplete = value; }
    public boolean isPostMaintenanceTestPassed() { return postMaintenanceTestPassed; }
    public void setPostMaintenanceTestPassed(boolean value) { this.postMaintenanceTestPassed = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
