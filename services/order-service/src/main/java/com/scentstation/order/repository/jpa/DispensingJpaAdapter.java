package com.scentstation.order.repository.jpa;

import com.scentstation.order.entity.DispensingCommand;
import com.scentstation.order.repository.DispensingRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class DispensingJpaAdapter implements DispensingRepository {

    private final SpringDataOrderRepository orders;
    private final SpringDataDispensingRepository repository;

    public DispensingJpaAdapter(
            SpringDataOrderRepository orders,
            SpringDataDispensingRepository repository
    ) {
        this.orders = orders;
        this.repository = repository;
    }

    @Override
    @Transactional
    public DispensingCommand saveIfAbsent(DispensingCommand command) {
        orders.findByIdForUpdate(command.getOrderId());
        return repository.findByOrderId(command.getOrderId())
                .orElseGet(() -> repository.save(command));
    }

    @Override
    public DispensingCommand save(DispensingCommand command) {
        return repository.save(command);
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
        repository.deleteAll();
    }
}
