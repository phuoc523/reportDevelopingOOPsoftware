package com.scentstation.catalog.exception;

public class TrustedActorContextException extends RuntimeException {

    // Tạo lỗi khi request không có trusted actor context hợp lệ.
    public TrustedActorContextException(String message) {
        super(message);
    }
}
