package com.scentstation.catalog.exception;

public class TenantClaimMismatchException extends RuntimeException {

    // Tạo lỗi khi tenant do caller khai báo khác tenant đã được xác minh.
    public TenantClaimMismatchException() {
        super("Tenant claim does not match verified tenant context");
    }
}
