package com.scentstation.simulator;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeviceSimulatorApplication {
    private static final Logger log = LoggerFactory.getLogger(DeviceSimulatorApplication.class);

    private DeviceSimulatorApplication() {}

    public static void main(String[] args) throws Exception {
        var mapper = MqttDeviceSimulator.newObjectMapper();
        SimulatorConfig config = SimulatorConfig.load(mapper);
        DeviceStateStore store = new DeviceStateStore(mapper, config.stateFile());
        SimulatorDeviceState state = new SimulatorDeviceState(config.slots(), store, config.initialMode());
        try (MqttDeviceSimulator simulator = new MqttDeviceSimulator(config, mapper, store, state)) {
            CountDownLatch stopped = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                simulator.close();
                stopped.countDown();
            }, "simulator-shutdown"));
            simulator.start();
            log.info("ScentStation simulator starting: machine={}, broker={}, slots={}, mode={}",
                    config.machineId(), config.brokerUri(), config.slots().size(), config.initialMode());
            Thread console = new Thread(() -> runConsole(simulator, stopped), "simulator-console");
            console.setDaemon(true);
            console.start();
            stopped.await();
        }
    }

    private static void runConsole(MqttDeviceSimulator simulator, CountDownLatch stopped) {
        if (System.console() == null) return;
        System.out.println("Commands: status | mode NORMAL|DOOR_OPEN|LEAK|SENSOR_ERROR|MAINTENANCE | ");
        System.out.println("          slot <id> empty | slot <id> fill <ml> | slot <id> sensor NORMAL|ERROR");
        System.out.println("          network down|up | quit");
        try (Scanner scanner = new Scanner(System.in)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;
                try {
                    if (line.equalsIgnoreCase("quit")) {
                        stopped.countDown();
                        return;
                    }
                    applyConsoleCommand(simulator, line);
                } catch (RuntimeException | IOException exception) {
                    System.err.println("Command rejected: " + exception.getMessage());
                }
            }
        }
    }

    private static void applyConsoleCommand(MqttDeviceSimulator simulator, String line) throws IOException {
        String[] parts = line.split("\\s+");
        switch (parts[0].toLowerCase()) {
            case "status" -> {
                System.out.printf("connected=%s mode=%s%n", simulator.isConnected(), simulator.state().mode());
                for (var slot : simulator.state().slots()) {
                    System.out.printf("slot=%s product=%s remainingMl=%.3f weightGrams=%.2f sensor=%s%n",
                            slot.id(), slot.productId(), slot.remainingMl(), slot.weightGrams(), slot.sensorStatus());
                }
            }
            case "mode" -> {
                if (parts.length != 2) throw new IllegalArgumentException("Use: mode <fault-mode>");
                simulator.processor().setMode(SimulatorConfig.FaultMode.valueOf(parts[1].toUpperCase()));
            }
            case "slot" -> updateSlot(simulator, parts);
            case "network" -> {
                if (parts.length != 2) throw new IllegalArgumentException("Use: network down|up");
                if ("down".equalsIgnoreCase(parts[1])) simulator.networkAvailable(false);
                else if ("up".equalsIgnoreCase(parts[1])) simulator.networkAvailable(true);
                else throw new IllegalArgumentException("Use: network down|up");
            }
            default -> throw new IllegalArgumentException("Unknown simulator command");
        }
    }

    private static void updateSlot(MqttDeviceSimulator simulator, String[] parts) throws IOException {
        if (parts.length < 3) throw new IllegalArgumentException("Use: slot <id> empty|fill <ml>|sensor NORMAL|ERROR");
        var slot = simulator.state().slot(parts[1]);
        if (slot == null) throw new IllegalArgumentException("Unknown slot " + parts[1]);
        switch (parts[2].toLowerCase()) {
            case "empty" -> slot.setRemainingMl(0);
            case "fill" -> {
                if (parts.length != 4) throw new IllegalArgumentException("Use: slot <id> fill <ml>");
                slot.setRemainingMl(Double.parseDouble(parts[3]));
            }
            case "sensor" -> {
                if (parts.length != 4 || !(parts[3].equalsIgnoreCase("NORMAL") || parts[3].equalsIgnoreCase("ERROR"))) {
                    throw new IllegalArgumentException("Use: slot <id> sensor NORMAL|ERROR");
                }
                slot.sensorStatus(parts[3].toUpperCase());
            }
            default -> throw new IllegalArgumentException("Unknown slot action");
        }
    }
}
