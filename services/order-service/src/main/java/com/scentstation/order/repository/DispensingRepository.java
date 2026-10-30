package com.scentstation.order.repository;

import com.scentstation.order.entity.DispensingCommand;

import java.util.Optional;

public interface DispensingRepository {

    DispensingCommand saveIfAbsent(DispensingCommand command);

    Optional<DispensingCommand> findByOrderId(Long orderId);

    Optional<DispensingCommand> findByCommandId(String commandId);

    void deleteAll();
}
