package com.scentstation.simulator;

import com.scentstation.simulator.DeviceStateStore.SlotSnapshot;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SimulatorDeviceState {
    private final ConcurrentHashMap<String, SlotState> slots = new ConcurrentHashMap<>();
    private volatile SimulatorConfig.FaultMode mode;
    private volatile boolean networkAvailable = true;

    public SimulatorDeviceState(List<SimulatorConfig.SlotConfig> configuration,
                                 DeviceStateStore store,
                                 SimulatorConfig.FaultMode initialMode) throws IOException {
        Map<String, SlotSnapshot> configured = new LinkedHashMap<>();
        for (SimulatorConfig.SlotConfig slot : configuration) {
            configured.put(slot.slotId(), new SlotSnapshot(slot.remainingMl(),
                    slot.emptyBottleWeightGrams() + slot.remainingMl() * slot.densityGramsPerMl(),
                    slot.sensorStatus()));
        }
        Map<String, SlotSnapshot> persisted = store.initializeSlots(configured);
        for (SimulatorConfig.SlotConfig slot : configuration) {
            SlotSnapshot snapshot = persisted.getOrDefault(slot.slotId(), configured.get(slot.slotId()));
            slots.put(slot.slotId(), new SlotState(slot, snapshot.remainingMl(), snapshot.weightGrams(),
                    snapshot.sensorStatus(), store));
        }
        this.mode = initialMode;
    }

    public SimulatorConfig.FaultMode mode() { return mode; }
    public synchronized void mode(SimulatorConfig.FaultMode newMode) { mode = newMode; }
    public boolean networkAvailable() { return networkAvailable; }
    public void networkAvailable(boolean available) { networkAvailable = available; }
    public boolean doorOpen() { return mode == SimulatorConfig.FaultMode.DOOR_OPEN; }
    public boolean leakDetected() { return mode == SimulatorConfig.FaultMode.LEAK; }
    public String sensorStatus() { return mode == SimulatorConfig.FaultMode.SENSOR_ERROR ? "ERROR" : "NORMAL"; }
    public SlotState slot(String id) { return slots.get(id); }
    public List<SlotState> slots() { return slots.values().stream().sorted((a, b) -> a.id().compareTo(b.id())).toList(); }

    public synchronized boolean dispenseIfSafe(SlotState slot) throws IOException {
        if (mode != SimulatorConfig.FaultMode.NORMAL || slot == null
                || !"NORMAL".equalsIgnoreCase(slot.sensorStatus())) return false;
        return slot.dispense();
    }

    public final class SlotState {
        private final SimulatorConfig.SlotConfig configuration;
        private final DeviceStateStore store;
        private volatile double remainingMl;
        private volatile double weightGrams;
        private volatile String sensorStatus;

        private SlotState(SimulatorConfig.SlotConfig configuration, double remainingMl, double weightGrams,
                          String sensorStatus, DeviceStateStore store) {
            this.configuration = configuration;
            this.remainingMl = remainingMl;
            this.weightGrams = weightGrams;
            this.sensorStatus = sensorStatus;
            this.store = store;
        }

        public String id() { return configuration.slotId(); }
        public String productId() { return configuration.productId(); }
        public double doseMl() { return configuration.doseMl(); }
        public double remainingMl() { return remainingMl; }
        public double weightGrams() { return weightGrams; }
        public String sensorStatus() { return sensorStatus; }
        public void sensorStatus(String value) throws IOException {
            sensorStatus = value;
            persist();
        }

        public synchronized boolean hasEnoughStock() { return remainingMl + 1e-9 >= configuration.doseMl(); }

        public synchronized boolean dispense() throws IOException {
            if (!hasEnoughStock() || !"NORMAL".equalsIgnoreCase(sensorStatus)) return false;
            remainingMl = Math.max(0, remainingMl - configuration.doseMl());
            weightGrams = Math.max(configuration.emptyBottleWeightGrams(),
                    weightGrams - configuration.doseMl() * configuration.densityGramsPerMl());
            persist();
            return true;
        }

        public synchronized void setRemainingMl(double quantity) throws IOException {
            if (!Double.isFinite(quantity) || quantity < 0) throw new IllegalArgumentException("Stock must be non-negative");
            remainingMl = quantity;
            weightGrams = configuration.emptyBottleWeightGrams() + quantity * configuration.densityGramsPerMl();
            persist();
        }

        private void persist() throws IOException {
            store.updateSlot(id(), new SlotSnapshot(remainingMl, weightGrams, sensorStatus));
        }
    }
}
