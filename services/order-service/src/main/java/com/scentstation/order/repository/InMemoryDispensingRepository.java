package com.scentstation.order.repository;

import com.scentstation.order.entity.DispensingCommand;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryDispensingRepository implements DispensingRepository {

    private final ConcurrentMap<Long, DispensingCommand> commandsByOrderId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, DispensingCommand> commandsById = new ConcurrentHashMap<>();

    @Override
    public synchronized DispensingCommand saveIfAbsent(DispensingCommand command) {
        DispensingCommand existing = commandsByOrderId.putIfAbsent(command.getOrderId(), command);
        if (existing != null) {
            return existing;
        }
        commandsById.put(command.getCommandId(), command);
        return command;
    }

    @Override
    public Optional<DispensingCommand> findByOrderId(Long orderId) {
        return Optional.ofNullable(commandsByOrderId.get(orderId));
    }

    @Override
    public Optional<DispensingCommand> findByCommandId(String commandId) {
        return Optional.ofNullable(commandsById.get(commandId));
    }

    @Override
    public synchronized void deleteAll() {
        commandsByOrderId.clear();
        commandsById.clear();
    }
}
