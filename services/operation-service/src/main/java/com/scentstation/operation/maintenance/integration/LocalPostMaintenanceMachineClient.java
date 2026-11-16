package com.scentstation.operation.maintenance.integration;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile({"local", "test", "docker-local"})
public class LocalPostMaintenanceMachineClient
        implements PostMaintenanceMachineClient {

    // Mô phỏng ACK của Machine chỉ trong môi trường local và test.
    @Override
    public String requestPostMaintenanceTest(
            Long tenantId,
            Long machineId,
            Long ticketId
    ) {
        return "local-" + UUID.randomUUID();
    }
}
