package com.scentstation.operation.maintenance.exception;

public class PostMaintenanceTestNotFoundException extends RuntimeException {

    // Tạo lỗi khi không tìm thấy post-maintenance test trong đúng tenant.
    public PostMaintenanceTestNotFoundException(String externalTestId) {
        super("Post-maintenance test not found: " + externalTestId);
    }
}
