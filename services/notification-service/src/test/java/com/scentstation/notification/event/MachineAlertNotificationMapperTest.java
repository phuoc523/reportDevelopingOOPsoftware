package com.scentstation.notification.event;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MachineAlertNotificationMapperTest {
    private final MachineAlertNotificationMapper mapper = new MachineAlertNotificationMapper();

    @Test
    void mapsCriticalAlertToUserFacingContent() {
        var command = mapper.map(event("MACHINE_OFFLINE", "CRITICAL", "OPEN"), List.of("42"));

        assertEquals("Nghiêm trọng: Kiosk mất kết nối", command.title());
        assertEquals("CRITICAL", command.priority());
        assertEquals("MACHINE_ALERT", command.notificationType());
        assertTrue(command.content().contains("kiosk 22, vị trí 3"));
        assertTrue(command.content().contains("Mã cảnh báo: 11"));
        assertEquals(List.of("42"), command.recipientIds());
    }

    @Test
    void mapsResolvedAndEscalatedStatusesExplicitly() {
        assertEquals("Đã xử lý: Phát hiện rò rỉ",
            mapper.map(event("LEAK_DETECTED", "HIGH", "RESOLVED"), List.of("42")).title());
        assertEquals("Cần xử lý khẩn: Nước hoa sắp hết",
            mapper.map(event("LOW_STOCK", "MEDIUM", "ESCALATED"), List.of("42")).title());
    }

    private MachineAlertEvent event(String type, String severity, String status) {
        return new MachineAlertEvent(1, "event-1", 11L, "7", 22L, "3", type, severity, status,
            Instant.parse("2026-11-15T00:00:00Z"), "machine-service");
    }
}
