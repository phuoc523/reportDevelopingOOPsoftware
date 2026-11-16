package com.scentstation.notification.event;

import com.scentstation.notification.dto.command.CreateNotificationCommand;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MachineAlertNotificationMapper {
    public CreateNotificationCommand map(MachineAlertEvent event, List<String> recipientIds) {
        String label = switch (event.alertType()) {
            case "MACHINE_OFFLINE" -> "Kiosk mất kết nối";
            case "SLOT_EMPTY" -> "Đầu phun đã hết nước hoa";
            case "SENSOR_FAILURE" -> "Cảm biến gặp sự cố";
            case "ACTUATOR_FAILURE" -> "Bộ truyền động gặp sự cố";
            case "DOOR_OPEN" -> "Cửa kiosk đang mở";
            case "LEAK_DETECTED" -> "Phát hiện rò rỉ";
            case "DISPENSE_FAILED" -> "Xịt nước hoa thất bại";
            case "LOW_STOCK" -> "Nước hoa sắp hết";
            default -> throw new IllegalArgumentException("Unsupported machine alert type");
        };
        String title = switch (event.status()) {
            case "RESOLVED" -> "Đã xử lý: " + label;
            case "ESCALATED" -> "Cần xử lý khẩn: " + label;
            default -> switch (event.severity()) {
                case "CRITICAL" -> "Nghiêm trọng: " + label;
                case "HIGH" -> "Ưu tiên cao: " + label;
                default -> "Cảnh báo kiosk: " + label;
            };
        };
        String content = "%s tại kiosk %d, vị trí %s. Trạng thái: %s. Mức độ: %s. Mã cảnh báo: %d. Thời điểm: %s."
            .formatted(label, event.machineId(), event.locationId(), vietnameseStatus(event.status()),
                vietnameseSeverity(event.severity()),
                event.alertId(), event.occurredAt());
        return new CreateNotificationCommand(
            event.tenantId(), title, content, "MACHINE_ALERT", event.severity(), event.source(),
            event.eventId(), recipientIds);
    }

    private String vietnameseStatus(String status) {
        return switch (status) {
            case "OPEN" -> "Mới";
            case "ACKNOWLEDGED" -> "Đã xác nhận";
            case "ASSIGNED" -> "Đã phân công";
            case "ESCALATED" -> "Khẩn cấp";
            case "RESOLVED" -> "Đã xử lý";
            default -> status;
        };
    }

    private String vietnameseSeverity(String severity) {
        return switch (severity) {
            case "CRITICAL" -> "Nghiêm trọng";
            case "HIGH" -> "Cao";
            case "MEDIUM" -> "Trung bình";
            case "LOW" -> "Thấp";
            default -> severity;
        };
    }
}
