package com.scentstation.operation.maintenance.exception;

public class InvalidMaintenanceTicketException extends RuntimeException {

    // Tạo lỗi khi dữ liệu hoặc thao tác trên maintenance ticket không hợp lệ.
    public InvalidMaintenanceTicketException(String message) {
        super(message);
    }
}