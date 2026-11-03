package com.scentstation.operation.maintenance.integration;

public interface PostMaintenanceMachineClient {

    // Yêu cầu Machine chạy bài test sau bảo trì và trả mã theo dõi ngoài hệ thống.
    String requestPostMaintenanceTest(
            Long tenantId,
            Long machineId,
            Long ticketId
    );
}
