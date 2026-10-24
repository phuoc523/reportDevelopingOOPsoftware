package com.scentstation;

import jakarta.persistence.*;
import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "machine_slots", uniqueConstraints = {
    @UniqueConstraint(name = "uk_machine_slot_number", columnNames = {"machine_id", "slot_number"}),
    @UniqueConstraint(name = "uk_machine_slot_code", columnNames = {"machine_id", "slot_code"}),
    @UniqueConstraint(name = "uk_machine_slot_cartridge", columnNames = "cartridge_id")
}, indexes = {
    @Index(name = "idx_slot_machine", columnList = "machine_id"),
    @Index(name = "idx_slot_product", columnList = "product_id")
})
public class MachineSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "slot_number", nullable = false)
    private Integer slotNumber;

    @Column(name = "slot_code", nullable = false, length = 50)
    private String slotCode;

    // IDs belong to Catalog/Operation services. Do not use cross-service JPA foreign keys.
    @Column(name = "product_id", length = 100)
    private String productId;

    @Column(name = "cartridge_id", length = 100)
    private String cartridgeId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot_status", nullable = false, length = 20)
    private SlotStatus status = SlotStatus.EMPTY;

    @Column(name = "stock_threshold", nullable = false)
    private Integer stockThreshold = 0;

    @Column(name = "calibrated_spray_duration_ms", nullable = false)
    private Integer calibratedSprayDurationMs = 1000;

    @Column(name = "dose_ml")
    private Double doseMl;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = SlotStatus.EMPTY;
        if (stockThreshold == null) stockThreshold = 0;
        if (calibratedSprayDurationMs == null) calibratedSprayDurationMs = 1000;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getMachineId() { return machineId; }
    public void setMachineId(Long machineId) { this.machineId = machineId; }
    public Integer getSlotNumber() { return slotNumber; }
    public void setSlotNumber(Integer slotNumber) { this.slotNumber = slotNumber; }
    public String getSlotCode() { return slotCode; }
    public void setSlotCode(String slotCode) { this.slotCode = slotCode; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getCartridgeId() { return cartridgeId; }
    public void setCartridgeId(String cartridgeId) { this.cartridgeId = cartridgeId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public SlotStatus getStatus() { return status; }
    public void setStatus(SlotStatus status) { this.status = status; }
    public Integer getStockThreshold() { return stockThreshold; }
    public void setStockThreshold(Integer stockThreshold) { this.stockThreshold = stockThreshold; }
    public Integer getCalibratedSprayDurationMs() { return calibratedSprayDurationMs; }
    public void setCalibratedSprayDurationMs(Integer value) { this.calibratedSprayDurationMs = value; }
    public Double getDoseMl() { return doseMl; }
    public void setDoseMl(Double doseMl) { this.doseMl = doseMl; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
