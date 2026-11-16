package com.scentstation.operation.maintenance.service;

import com.scentstation.operation.maintenance.entity.MaintenanceAuditEvent;
import com.scentstation.operation.maintenance.repository.MaintenanceAuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MaintenanceAuditService {

    private final MaintenanceAuditEventRepository maintenanceAuditEventRepository;

    // Lưu audit event trong cùng transaction với nghiệp vụ gọi phương thức này.
    public MaintenanceAuditEvent record(MaintenanceAuditEvent event) {
        return maintenanceAuditEventRepository.save(event);
    }
}
