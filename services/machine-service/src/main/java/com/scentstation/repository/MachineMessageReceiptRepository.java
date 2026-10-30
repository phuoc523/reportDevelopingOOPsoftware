package com.scentstation.repository;

import com.scentstation.entity.MachineMessageReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MachineMessageReceiptRepository extends JpaRepository<MachineMessageReceipt, String> {
}
