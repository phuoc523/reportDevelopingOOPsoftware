package com.scentstation.order.repository.jpa;

import com.scentstation.order.entity.DispensingCommand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataDispensingRepository extends JpaRepository<DispensingCommand, String> {

    Optional<DispensingCommand> findByOrderId(Long orderId);
}
