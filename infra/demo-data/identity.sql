SET NAMES utf8mb4;

INSERT INTO brand_tenants
    (id, code, name, description, contact_email, contact_phone, logo_url, kiosk_content, status)
VALUES
    (1, 'scentstation', 'ScentStation', 'Thương hiệu demo ScentStation.',
     'contact@scentstation.demo', NULL, NULL,
     'Khám phá bộ sưu tập hương thơm ScentStation.', 'ACTIVE'),
    (9102, 'chanel', 'Chanel', 'Thương hiệu nước hoa Pháp với phong cách thanh lịch và vượt thời gian.',
     'admin.vn@chanel.demo', '028-7300-1921', NULL,
     'Khám phá các tầng hương biểu tượng của Chanel tại ScentStation.', 'ACTIVE'),
    (9103, 'dior', 'Dior', 'Thương hiệu nước hoa Pháp kết hợp nét cổ điển và tinh thần hiện đại.',
     'admin.vn@dior.demo', '028-7300-1947', NULL,
     'Trải nghiệm bộ sưu tập nước hoa Dior tại ScentStation.', 'ACTIVE')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    contact_email = VALUES(contact_email),
    contact_phone = VALUES(contact_phone),
    kiosk_content = VALUES(kiosk_content),
    status = VALUES(status);

-- VIEWER has been retired. Remove the old local demo account and all of its
-- role/scope rows before syncing the supported account set below.
DELETE FROM user_permission_grants WHERE user_id IN (SELECT id FROM users WHERE username = 'scent.viewer');
DELETE FROM user_location_scopes WHERE user_id IN (SELECT id FROM users WHERE username = 'scent.viewer');
DELETE FROM user_machine_scopes WHERE user_id IN (SELECT id FROM users WHERE username = 'scent.viewer');
DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username = 'scent.viewer');
DELETE FROM users WHERE username = 'scent.viewer';

-- All demo accounts use the local-only password: Demo@2026!
INSERT INTO users
    (id, username, email, password, full_name, role, active, locked, tenant_id, token_version)
VALUES
    (9199, 'platform.admin', 'platform.admin@scentstation.local',
     '$2a$10$n4KIuMxELWYN9baNNa61RuR5RbvS8JkT8S1YQ9xvsaT7vcElYQfO6',
     'Quản trị nền tảng ScentStation', 'PLATFORM_SUPER_ADMIN', 1, 0, NULL, 0),
    (9200, 'brand.admin', 'brand.admin@scentstation.local',
     '$2a$10$n4KIuMxELWYN9baNNa61RuR5RbvS8JkT8S1YQ9xvsaT7vcElYQfO6',
     'Quản trị thương hiệu ScentStation', 'BRAND_ADMIN', 1, 0, 1, 0),
    (9201, 'chanel.admin', 'admin.vn@chanel.demo',
     '$2a$10$n4KIuMxELWYN9baNNa61RuR5RbvS8JkT8S1YQ9xvsaT7vcElYQfO6',
     'Quản trị thương hiệu Chanel', 'BRAND_ADMIN', 1, 0, 9102, 0),
    (9202, 'dior.admin', 'admin.vn@dior.demo',
     '$2a$10$n4KIuMxELWYN9baNNa61RuR5RbvS8JkT8S1YQ9xvsaT7vcElYQfO6',
     'Quản trị thương hiệu Dior', 'BRAND_ADMIN', 1, 0, 9103, 0),
    (9203, 'scent.ops', 'ops@scentstation.demo',
     '$2a$10$n4KIuMxELWYN9baNNa61RuR5RbvS8JkT8S1YQ9xvsaT7vcElYQfO6',
     'Nguyễn Minh - Quản lý vận hành', 'OPERATIONS_MANAGER', 1, 0, 1, 0),
    (9204, 'scent.tech', 'tech@scentstation.demo',
     '$2a$10$n4KIuMxELWYN9baNNa61RuR5RbvS8JkT8S1YQ9xvsaT7vcElYQfO6',
     'Trần An - Kỹ thuật viên', 'TECHNICIAN', 1, 0, 1, 0),
    (9205, 'scent.inventory', 'inventory@scentstation.demo',
     '$2a$10$n4KIuMxELWYN9baNNa61RuR5RbvS8JkT8S1YQ9xvsaT7vcElYQfO6',
     'Lê Hà - Nhân viên kho', 'INVENTORY_STAFF', 1, 0, 1, 0),
    (9207, 'kiosk.customer', 'customer@scentstation.demo',
     '$2a$10$n4KIuMxELWYN9baNNa61RuR5RbvS8JkT8S1YQ9xvsaT7vcElYQfO6',
     'Khách hàng Kiosk Nguyễn Huệ', 'CUSTOMER', 1, 0, 1, 0)
ON DUPLICATE KEY UPDATE
    email = VALUES(email),
    token_version = IF(password <> VALUES(password), token_version + 1, token_version),
    password = VALUES(password),
    full_name = VALUES(full_name),
    role = VALUES(role),
    active = VALUES(active),
    locked = VALUES(locked),
    tenant_id = VALUES(tenant_id);

INSERT IGNORE INTO user_roles (user_id, role_name)
SELECT id, role FROM users
WHERE username IN ('platform.admin', 'brand.admin', 'chanel.admin', 'dior.admin',
                   'scent.ops', 'scent.tech', 'scent.inventory',
                   'kiosk.customer');

-- KIOSK_BROWSE currently has no dedicated read controller. These two least-scope
-- grants allow the local Kiosk to read only its tenant catalog and assigned machine.
INSERT IGNORE INTO user_permission_grants (user_id, permission_name)
SELECT id, 'CATALOG_READ' FROM users WHERE username = 'kiosk.customer';

INSERT IGNORE INTO user_permission_grants (user_id, permission_name)
SELECT id, 'MACHINE_READ' FROM users WHERE username = 'kiosk.customer';

INSERT IGNORE INTO user_permission_grants (user_id, permission_name)
SELECT id, 'DIAGNOSTIC_SPRAY' FROM users WHERE username = 'scent.tech';

INSERT IGNORE INTO user_permission_grants (user_id, permission_name)
SELECT id, 'INVENTORY_ADJUST' FROM users WHERE username = 'scent.inventory';

INSERT IGNORE INTO user_location_scopes (user_id, location_id)
SELECT id, 9101 FROM users WHERE username = 'scent.tech';

INSERT IGNORE INTO user_machine_scopes (user_id, machine_id)
SELECT id, 9101 FROM users WHERE username = 'scent.tech';

INSERT IGNORE INTO user_machine_scopes (user_id, machine_id)
SELECT id, 9101 FROM users WHERE username = 'kiosk.customer';
