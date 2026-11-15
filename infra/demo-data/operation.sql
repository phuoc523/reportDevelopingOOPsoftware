SET NAMES utf8mb4;

INSERT INTO inventory_batches
    (id, tenant_id, product_id, batch_code, received_quantity, received_at, expiry_date, created_at, updated_at)
VALUES
    (91201, 1, 91001, 'SS-SIG-2026-01', 20, CURRENT_DATE - INTERVAL 45 DAY, CURRENT_DATE + INTERVAL 320 DAY, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91206, 1, 91006, 'SS-CIT-2026-01', 20, CURRENT_DATE - INTERVAL 32 DAY, CURRENT_DATE + INTERVAL 335 DAY, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91207, 1, 91007, 'SS-ROS-2026-01', 16, CURRENT_DATE - INTERVAL 28 DAY, CURRENT_DATE + INTERVAL 310 DAY, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91208, 1, 91008, 'SS-AMB-2026-01', 14, CURRENT_DATE - INTERVAL 22 DAY, CURRENT_DATE + INTERVAL 300 DAY, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91202, 9102, 91002, 'CH-BLEU-2026-01', 18, CURRENT_DATE - INTERVAL 35 DAY, CURRENT_DATE + INTERVAL 330 DAY, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91203, 9102, 91003, 'CH-COCO-2026-01', 12, CURRENT_DATE - INTERVAL 60 DAY, CURRENT_DATE + INTERVAL 280 DAY, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91204, 9103, 91004, 'DR-SAUV-2026-01', 20, CURRENT_DATE - INTERVAL 25 DAY, CURRENT_DATE + INTERVAL 340 DAY, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91205, 9103, 91005, 'DR-JADO-2026-01', 10, CURRENT_DATE - INTERVAL 90 DAY, CURRENT_DATE + INTERVAL 240 DAY, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    product_id = VALUES(product_id),
    batch_code = VALUES(batch_code),
    received_quantity = VALUES(received_quantity),
    received_at = VALUES(received_at),
    expiry_date = VALUES(expiry_date),
    updated_at = CURRENT_TIMESTAMP(6);

INSERT INTO bottles
    (id, tenant_id, product_id, batch_id, bottle_code, initial_quantity, current_quantity,
     calibrated_consumption_per_spray, status, created_at, updated_at)
VALUES
    (91301, 1, 91001, 91201, 'BOT-SS-91001', 500.000, 452.000, 0.120, 'INSTALLED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91306, 1, 91006, 91206, 'BOT-SS-91006', 500.000, 438.000, 0.120, 'INSTALLED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91307, 1, 91007, 91207, 'BOT-SS-91007', 500.000, 401.000, 0.120, 'INSTALLED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91308, 1, 91008, 91208, 'BOT-SS-91008', 500.000, 365.000, 0.120, 'INSTALLED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91302, 9102, 91002, 91202, 'BOT-CH-91002', 500.000, 386.000, 0.120, 'INSTALLED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91303, 9102, 91003, 91203, 'BOT-CH-91003', 500.000, 24.000, 0.120, 'NEARLY_EMPTY', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91304, 9103, 91004, 91204, 'BOT-DR-91004', 500.000, 421.000, 0.120, 'INSTALLED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (91305, 9103, 91005, 91205, 'BOT-DR-91005', 500.000, 0.000, 0.120, 'EMPTY', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    product_id = VALUES(product_id),
    batch_id = VALUES(batch_id),
    bottle_code = VALUES(bottle_code),
    initial_quantity = VALUES(initial_quantity),
    current_quantity = VALUES(current_quantity),
    calibrated_consumption_per_spray = VALUES(calibrated_consumption_per_spray),
    status = VALUES(status),
    updated_at = CURRENT_TIMESTAMP(6);

INSERT INTO bottle_installations
    (id, tenant_id, bottle_id, machine_id, slot_id, installed_by, installed_at,
     quantity_before_installation, active)
VALUES
    (91401, 1, 91301, 9101, 91001, 9203, CURRENT_TIMESTAMP(6) - INTERVAL 30 DAY, 500.000, 1),
    (91406, 1, 91306, 9101, 91006, 9203, CURRENT_TIMESTAMP(6) - INTERVAL 24 DAY, 500.000, 1),
    (91407, 1, 91307, 9101, 91007, 9203, CURRENT_TIMESTAMP(6) - INTERVAL 20 DAY, 500.000, 1),
    (91408, 1, 91308, 9101, 91008, 9203, CURRENT_TIMESTAMP(6) - INTERVAL 18 DAY, 500.000, 1),
    (91402, 9102, 91302, 9102, 91002, 9201, CURRENT_TIMESTAMP(6) - INTERVAL 25 DAY, 500.000, 1),
    (91403, 9102, 91303, 9102, 91003, 9201, CURRENT_TIMESTAMP(6) - INTERVAL 40 DAY, 500.000, 1),
    (91404, 9103, 91304, 9103, 91004, 9202, CURRENT_TIMESTAMP(6) - INTERVAL 20 DAY, 500.000, 1)
ON DUPLICATE KEY UPDATE
    bottle_id = VALUES(bottle_id),
    machine_id = VALUES(machine_id),
    slot_id = VALUES(slot_id),
    installed_by = VALUES(installed_by),
    installed_at = VALUES(installed_at),
    quantity_before_installation = VALUES(quantity_before_installation),
    active = VALUES(active),
    removed_by = NULL,
    removed_at = NULL,
    quantity_after_removal = NULL;

INSERT INTO bottle_movements
    (id, tenant_id, bottle_id, movement_type, machine_id, slot_id,
     quantity_before, quantity_after, actor_id, reason, reference_id, created_at)
VALUES
    (91501, 1, 91301, 'INSTALL', 9101, 91001, 500.000, 500.000, 9203, 'Lắp chai demo vào đầu phun', 'DEMO-INSTALL-91301', CURRENT_TIMESTAMP(6) - INTERVAL 30 DAY),
    (91506, 1, 91306, 'INSTALL', 9101, 91006, 500.000, 500.000, 9203, 'Lắp chai Citrus Veil vào đầu phun', 'DEMO-INSTALL-91306', CURRENT_TIMESTAMP(6) - INTERVAL 24 DAY),
    (91507, 1, 91307, 'INSTALL', 9101, 91007, 500.000, 500.000, 9203, 'Lắp chai Rose Atelier vào đầu phun', 'DEMO-INSTALL-91307', CURRENT_TIMESTAMP(6) - INTERVAL 20 DAY),
    (91508, 1, 91308, 'INSTALL', 9101, 91008, 500.000, 500.000, 9203, 'Lắp chai Amber Nuit vào đầu phun', 'DEMO-INSTALL-91308', CURRENT_TIMESTAMP(6) - INTERVAL 18 DAY),
    (91502, 9102, 91302, 'INSTALL', 9102, 91002, 500.000, 500.000, 9201, 'Lắp chai Bleu de Chanel', 'DEMO-INSTALL-91302', CURRENT_TIMESTAMP(6) - INTERVAL 25 DAY),
    (91503, 9102, 91303, 'INSTALL', 9102, 91003, 500.000, 500.000, 9201, 'Lắp chai Coco Mademoiselle', 'DEMO-INSTALL-91303', CURRENT_TIMESTAMP(6) - INTERVAL 40 DAY),
    (91504, 9103, 91304, 'INSTALL', 9103, 91004, 500.000, 500.000, 9202, 'Lắp chai Dior Sauvage', 'DEMO-INSTALL-91304', CURRENT_TIMESTAMP(6) - INTERVAL 20 DAY),
    (91505, 9103, 91305, 'ADJUSTMENT', NULL, NULL, 12.000, 0.000, 9202, 'Xác nhận chai J''adore đã hết', 'DEMO-EMPTY-91305', CURRENT_TIMESTAMP(6) - INTERVAL 1 DAY)
ON DUPLICATE KEY UPDATE
    quantity_before = VALUES(quantity_before),
    quantity_after = VALUES(quantity_after),
    reason = VALUES(reason),
    created_at = VALUES(created_at);

INSERT INTO maintenance_tickets
    (id, tenant_id, machine_id, location_id, slot_id, bottle_id, title, description,
     category, severity, priority, status, reported_by_actor_id, assigned_to_actor_id,
     due_at, critical, created_at, updated_at)
VALUES
    (91601, 9102, 9102, 9102, 91003, 91303,
     'Thay chai Coco Mademoiselle sắp hết',
     'Tồn kho tại đầu phun số 2 đã xuống dưới ngưỡng cảnh báo.',
     'INVENTORY', 'MODERATE', 'HIGH', 'ASSIGNED', 9201, 9201,
     CURRENT_TIMESTAMP(6) + INTERVAL 1 DAY, 0, CURRENT_TIMESTAMP(6) - INTERVAL 3 HOUR, CURRENT_TIMESTAMP(6)),
    (91602, 9103, 9103, 9103, NULL, NULL,
     'Kiểm tra kết nối kiosk Dior Đồng Khởi',
     'Thiết bị không gửi heartbeat trong hơn 30 phút.',
     'CONNECTIVITY', 'HIGH', 'URGENT', 'OPEN', 9202, NULL,
     CURRENT_TIMESTAMP(6) + INTERVAL 4 HOUR, 1, CURRENT_TIMESTAMP(6) - INTERVAL 30 MINUTE, CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    description = VALUES(description),
    category = VALUES(category),
    severity = VALUES(severity),
    priority = VALUES(priority),
    status = VALUES(status),
    assigned_to_actor_id = VALUES(assigned_to_actor_id),
    due_at = VALUES(due_at),
    critical = VALUES(critical),
    updated_at = CURRENT_TIMESTAMP(6);
