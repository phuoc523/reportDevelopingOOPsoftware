package com.scentstation.operation.maintenance.exception;

public class MaintenanceNotFoundException extends RuntimeException {

    // Tạo lỗi khi không tìm thấy maintenance ticket trong đúng tenant.
    public MaintenanceNotFoundException(Long ticketId) {
        super("Maintenance ticket not found: " + ticketId);
    }
}