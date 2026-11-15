SET NAMES utf8mb4;

INSERT INTO machine_locations
    (location_id, tenant_id, name, address, active, created_at, updated_at)
VALUES
    ('9101', '1', 'ScentStation Nguyễn Huệ', '42 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh', 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('9102', '9102', 'Chanel Tràng Tiền', '24 Tràng Tiền, Hoàn Kiếm, Hà Nội', 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('9103', '9103', 'Dior Đồng Khởi', '88 Đồng Khởi, Quận 1, TP. Hồ Chí Minh', 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    tenant_id = VALUES(tenant_id),
    name = VALUES(name),
    address = VALUES(address),
    active = VALUES(active),
    updated_at = CURRENT_TIMESTAMP(6);

INSERT INTO machines
    (id, tenant_id, location_id, name, serial_number, device_identity,
     device_registration_status, operational_status, last_seen_at,
     operating_hours_start, operating_hours_end, maintenance_checklist_complete,
     post_maintenance_test_passed, created_at, updated_at)
VALUES
    (9101, '1', '9101', 'Kiosk ScentStation Nguyễn Huệ', 'SS-DEMO-9101', 'device-ss-demo-9101',
     'ACTIVE', 'ONLINE', CURRENT_TIMESTAMP(6), '08:00:00', '22:00:00', 1, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (9102, '9102', '9102', 'Kiosk Chanel Tràng Tiền', 'CH-DEMO-9102', 'device-ch-demo-9102',
     'ACTIVE', 'ONLINE', CURRENT_TIMESTAMP(6), '09:00:00', '21:30:00', 1, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (9103, '9103', '9103', 'Kiosk Dior Đồng Khởi', 'DR-DEMO-9103', 'device-dr-demo-9103',
     'ACTIVE', 'OFFLINE', CURRENT_TIMESTAMP(6) - INTERVAL 35 MINUTE, '09:00:00', '22:00:00', 1, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    tenant_id = VALUES(tenant_id),
    location_id = VALUES(location_id),
    name = VALUES(name),
    device_registration_status = VALUES(device_registration_status),
    operational_status = VALUES(operational_status),
    last_seen_at = VALUES(last_seen_at),
    operating_hours_start = VALUES(operating_hours_start),
    operating_hours_end = VALUES(operating_hours_end),
    maintenance_checklist_complete = VALUES(maintenance_checklist_complete),
    post_maintenance_test_passed = VALUES(post_maintenance_test_passed),
    updated_at = CURRENT_TIMESTAMP(6);

-- First completed brand: ScentStation has four physical nozzles, one product per nozzle.
-- Chanel and Dior keep their current partial data until the next seed expansion.
INSERT INTO machine_slots
    (id, machine_id, slot_number, slot_code, product_id, cartridge_id, price,
     calibrated_spray_duration_ms, dose_ml, enabled, slot_status, stock_threshold,
     created_at, updated_at)
VALUES
    (91001, 9101, 1, 'SS-N01', '91001', 'CART-SS-91001', 45000.00, 420, 0.120, 1, 'AVAILABLE', 50, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91006, 9101, 2, 'SS-N02', '91006', 'CART-SS-91006', 39000.00, 410, 0.120, 1, 'AVAILABLE', 50, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91007, 9101, 3, 'SS-N03', '91007', 'CART-SS-91007', 42000.00, 425, 0.120, 1, 'AVAILABLE', 50, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91008, 9101, 4, 'SS-N04', '91008', 'CART-SS-91008', 48000.00, 440, 0.120, 1, 'AVAILABLE', 50, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91002, 9102, 1, 'CH-N01', '91002', 'CART-CH-91002', 65000.00, 430, 0.120, 1, 'AVAILABLE', 50, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91003, 9102, 2, 'CH-N02', '91003', 'CART-CH-91003', 70000.00, 440, 0.120, 1, 'AVAILABLE', 50, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91004, 9103, 1, 'DR-N01', '91004', 'CART-DR-91004', 68000.00, 425, 0.120, 1, 'AVAILABLE', 50, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    machine_id = VALUES(machine_id),
    slot_number = VALUES(slot_number),
    slot_code = VALUES(slot_code),
    product_id = VALUES(product_id),
    cartridge_id = VALUES(cartridge_id),
    price = VALUES(price),
    calibrated_spray_duration_ms = VALUES(calibrated_spray_duration_ms),
    dose_ml = VALUES(dose_ml),
    enabled = VALUES(enabled),
    slot_status = VALUES(slot_status),
    stock_threshold = VALUES(stock_threshold),
    updated_at = CURRENT_TIMESTAMP(6);

INSERT INTO machine_alerts
    (id, tenant_id, machine_id, location_id, slot_id, alert_type, alert_source,
     severity, status, summary, dedupe_key, first_seen_at, last_seen_at, reopen_count)
VALUES
    (91201, '9102', 9102, '9102', 91003, 'LOW_STOCK', 'INVENTORY',
     'MEDIUM', 'OPEN', 'Coco Mademoiselle sắp hết, cần bổ sung chai mới.',
     'demo:9102:91003:low-stock', CURRENT_TIMESTAMP(6) - INTERVAL 3 HOUR, CURRENT_TIMESTAMP(6), 0),
    (91202, '9103', 9103, '9103', NULL, 'MACHINE_OFFLINE', 'MONITOR',
     'HIGH', 'ACKNOWLEDGED', 'Kiosk Dior Đồng Khởi mất kết nối hơn 30 phút.',
     'demo:9103:offline', CURRENT_TIMESTAMP(6) - INTERVAL 35 MINUTE, CURRENT_TIMESTAMP(6), 0)
ON DUPLICATE KEY UPDATE
    severity = VALUES(severity),
    status = VALUES(status),
    summary = VALUES(summary),
    last_seen_at = CURRENT_TIMESTAMP(6);
