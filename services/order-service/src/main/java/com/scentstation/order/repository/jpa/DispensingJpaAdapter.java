package com.scentstation.order.repository.jpa;

import com.scentstation.order.entity.DispensingCommand;
import com.scentstation.order.entity.DispenseEventOutbox;
import com.scentstation.order.entity.DispensingStatus;
import com.scentstation.order.repository.DispensingRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class DispensingJpaAdapter implements DispensingRepository {

    private final SpringDataOrderRepository orders;
    private final SpringDataDispensingRepository repository;
    private final DispenseEventOutboxRepository dispenseOutbox;

    public DispensingJpaAdapter(
            SpringDataOrderRepository orders,
            SpringDataDispensingRepository repository,
            DispenseEventOutboxRepository dispenseOutbox
    ) {
        this.orders = orders;
        this.repository = repository;
        this.dispenseOutbox = dispenseOutbox;
    }

    @Override
    @Transactional
    public DispensingCommand saveIfAbsent(DispensingCommand command) {
        orders.findByIdForUpdate(command.getOrderId());
        return repository.findByOrderId(command.getOrderId())
                .orElseGet(() -> repository.save(command));
    }

    @Override
    @Transactional
    public DispensingCommand save(DispensingCommand command) {
        DispensingCommand stored = repository.save(command);
        enqueueSuccessfulDispense(stored);
        return stored;
    }

    @Override
    public Optional<DispensingCommand> findByOrderId(Long orderId) {
        return repository.findByOrderId(orderId);
    }

    @Override
    public Optional<DispensingCommand> findByCommandId(String commandId) {
        return repository.findById(commandId);
    }

    @Override
    @Transactional
    public void deleteAll() {
        dispenseOutbox.deleteAll();
        repository.deleteAll();
    }

    private void enqueueSuccessfulDispense(DispensingCommand command) {
        if (command.getStatus() != DispensingStatus.DISPENSED) {
            return;
        }
        orders.findById(command.getOrderId()).ifPresent(order -> {
            Long tenantId = positiveLong(order.getTenantId());
            Long machineId = positiveLong(command.getMachineId());
            Long slotId = positiveLong(command.getSlotId());
            if (tenantId == null || machineId == null || slotId == null) {
                return;
            }
            DispenseEventOutbox event = new DispenseEventOutbox(
                command.getCommandId(), tenantId, machineId, slotId);
            if (!dispenseOutbox.existsById(event.getEventId())) {
                dispenseOutbox.save(event);
            }
        });
    }

    private Long positiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
