package com.scentstation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "machine_telemetry_latest", uniqueConstraints =
    @UniqueConstraint(name = "uk_telemetry_machine_slot", columnNames = {"machine_id", "slot_id"}))
public class MachineTelemetryLatest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "remaining_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal remainingPercent;

    @Column(name = "sensor_status", nullable = false, length = 30)
    private String sensorStatus;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected MachineTelemetryLatest() {
    }

    public MachineTelemetryLatest(Long machineId, Long slotId, BigDecimal remainingPercent,
                                  String sensorStatus, Instant measuredAt, Instant receivedAt) {
        this.machineId = machineId;
        this.slotId = slotId;
        update(remainingPercent, sensorStatus, measuredAt, receivedAt);
    }

    public void update(BigDecimal remainingPercent, String sensorStatus,
                       Instant measuredAt, Instant receivedAt) {
        if (this.measuredAt != null && !measuredAt.isAfter(this.measuredAt)) {
            return;
        }
        this.remainingPercent = remainingPercent;
        this.sensorStatus = sensorStatus;
        this.measuredAt = measuredAt;
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public Long getMachineId() { return machineId; }
    public Long getSlotId() { return slotId; }
    public BigDecimal getRemainingPercent() { return remainingPercent; }
    public String getSensorStatus() { return sensorStatus; }
    public Instant getMeasuredAt() { return measuredAt; }
    public Instant getReceivedAt() { return receivedAt; }
}
