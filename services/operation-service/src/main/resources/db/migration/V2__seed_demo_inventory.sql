INSERT INTO inventory_batches
    (id, tenant_id, product_id, batch_code, received_quantity, received_at, expiry_date, created_at, updated_at)
VALUES
    (91201, 1, 91001, 'SS-SIG-2026-01', 1, '2026-01-01', '2028-01-01', NOW(6), NOW(6)),
    (91206, 1, 91006, 'SS-CIT-2026-01', 1, '2026-01-01', '2028-01-01', NOW(6), NOW(6)),
    (91207, 1, 91007, 'SS-ROS-2026-01', 1, '2026-01-01', '2028-01-01', NOW(6), NOW(6)),
    (91208, 1, 91008, 'SS-AMB-2026-01', 1, '2026-01-01', '2028-01-01', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE product_id = VALUES(product_id), updated_at = NOW(6);

INSERT INTO bottles
    (id, tenant_id, product_id, batch_id, bottle_code, initial_quantity, current_quantity,
     calibrated_consumption_per_spray, status, created_at, updated_at)
VALUES
    (91301, 1, 91001, 91201, 'BOT-SS-91001', 500.000, 452.000, 0.120, 'INSTALLED', NOW(6), NOW(6)),
    (91306, 1, 91006, 91206, 'BOT-SS-91006', 500.000, 438.000, 0.120, 'INSTALLED', NOW(6), NOW(6)),
    (91307, 1, 91007, 91207, 'BOT-SS-91007', 500.000, 401.000, 0.120, 'INSTALLED', NOW(6), NOW(6)),
    (91308, 1, 91008, 91208, 'BOT-SS-91008', 500.000, 0.240, 0.120, 'INSTALLED', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE
    calibrated_consumption_per_spray = VALUES(calibrated_consumption_per_spray),
    current_quantity = IF(id = 91308 AND current_quantity > 0.240, 0.240, current_quantity),
    updated_at = NOW(6);

INSERT IGNORE INTO bottle_installations
    (id, tenant_id, bottle_id, machine_id, slot_id, installed_by, installed_at,
     quantity_before_installation, active)
VALUES
    (91401, 1, 91301, 9101, 91001, 1, NOW(6), 452.000, TRUE),
    (91406, 1, 91306, 9101, 91006, 1, NOW(6), 438.000, TRUE),
    (91407, 1, 91307, 9101, 91007, 1, NOW(6), 401.000, TRUE),
    (91408, 1, 91308, 9101, 91008, 1, NOW(6), 0.240, TRUE);
