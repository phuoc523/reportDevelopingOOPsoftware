package com.scentstation.operation.maintenance.exception;

public class CriticalAlertAlreadyProcessedException
        extends RuntimeException {

    // Tạo lỗi khi cùng một critical alert đã được xử lý trước đó.
    public CriticalAlertAlreadyProcessedException(
            String alertId
    ) {
        super(
                "Critical maintenance alert already processed: "
                        + alertId
        );
    }
}