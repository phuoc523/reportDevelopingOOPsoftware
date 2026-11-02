package com.scentstation.operation.maintenance.exception;

public class MaintenanceChecklistNotFoundException
        extends RuntimeException {

    // Tạo lỗi khi không tìm thấy checklist item trong đúng tenant.
    public MaintenanceChecklistNotFoundException(Long checklistItemId) {
        super(
                "Maintenance checklist item not found: "
                        + checklistItemId
        );
    }
}