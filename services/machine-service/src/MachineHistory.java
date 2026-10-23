package com.scentstation;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "machine_history", indexes = {
    @Index(name = "idx_machine_history_machine_time", columnList = "machine_id, occurred_at")
})
public class MachineHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MachineHistoryAction action;

    @Column(name = "old_tenant_id", length = 100)
    private String oldTenantId;
    @Column(name = "new_tenant_id", length = 100)
    private String newTenantId;
    @Column(name = "old_location_id", length = 100)
    private String oldLocationId;
    @Column(name = "new_location_id", length = 100)
    private String newLocationId;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected MachineHistory() {}

    public MachineHistory(Long machineId, MachineHistoryAction action, String oldTenantId,
                          String newTenantId, String oldLocationId, String newLocationId,
                          String details, String actorId) {
        this.machineId = machineId;
        this.action = action;
        this.oldTenantId = oldTenantId;
        this.newTenantId = newTenantId;
        this.oldLocationId = oldLocationId;
        this.newLocationId = newLocationId;
        this.details = details;
        this.actorId = actorId;
        this.occurredAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getMachineId() { return machineId; }
    public MachineHistoryAction getAction() { return action; }
    public String getOldTenantId() { return oldTenantId; }
    public String getNewTenantId() { return newTenantId; }
    public String getOldLocationId() { return oldLocationId; }
    public String getNewLocationId() { return newLocationId; }
    public String getDetails() { return details; }
    public String getActorId() { return actorId; }
    public Instant getOccurredAt() { return occurredAt; }
}
