-- ScentStation Machine Service schema
-- Run this file in MySQL Workbench.
-- IDs tenant_id, location_id, product_id and cartridge_id are external service identifiers.
-- They intentionally do not reference tables in other microservices.
CREATE DATABASE IF NOT EXISTS scentstation_machine
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE scentstation_machine;

CREATE TABLE IF NOT EXISTS machines (
    id BIGINT NOT NULL AUTO_INCREMENT,
    serial_number VARCHAR(100) NOT NULL,
    device_identity VARCHAR(150) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    location_id VARCHAR(100) NOT NULL,
    name VARCHAR(150) NOT NULL,
    device_registration_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    operational_status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    operating_hours_start TIME NULL,
    operating_hours_end TIME NULL,
    last_seen_at TIMESTAMP(6) NULL,
    last_heartbeat_event_id VARCHAR(100) NULL,
    firmware_version VARCHAR(100) NULL,
    configuration_version VARCHAR(100) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_machine_serial_number UNIQUE (serial_number),
    CONSTRAINT uk_machine_device_identity UNIQUE (device_identity),
    INDEX idx_machine_tenant (tenant_id),
    INDEX idx_machine_location (location_id),
    CONSTRAINT chk_machine_device_registration_status
      CHECK (device_registration_status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT chk_machine_operational_status
      CHECK (operational_status IN ('ONLINE', 'OFFLINE', 'UNSTABLE', 'DISABLED', 'MAINTENANCE', 'ERROR'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS machine_slots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    machine_id BIGINT NOT NULL,
    slot_number INT NOT NULL,
    slot_code VARCHAR(50) NOT NULL,
    product_id VARCHAR(100) NULL,
    cartridge_id VARCHAR(100) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    slot_status VARCHAR(20) NOT NULL DEFAULT 'EMPTY',
    stock_threshold INT NOT NULL DEFAULT 0,
    calibrated_spray_duration_ms INT NOT NULL DEFAULT 1000,
    dose_ml DOUBLE NULL,
    price DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_machine_slot_number UNIQUE (machine_id, slot_number),
    CONSTRAINT uk_machine_slot_code UNIQUE (machine_id, slot_code),
    CONSTRAINT uk_machine_slot_cartridge UNIQUE (cartridge_id),
    CONSTRAINT fk_machine_slots_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
      ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_slot_machine (machine_id),
    INDEX idx_slot_product (product_id),
    CONSTRAINT chk_slot_status CHECK (slot_status IN ('AVAILABLE', 'EMPTY', 'DISABLED', 'ERROR')),
    CONSTRAINT chk_slot_number CHECK (slot_number >= 1),
    CONSTRAINT chk_stock_threshold CHECK (stock_threshold >= 0),
    CONSTRAINT chk_spray_duration CHECK (calibrated_spray_duration_ms > 0),
    CONSTRAINT chk_dose_ml CHECK (dose_ml IS NULL OR dose_ml > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS machine_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    machine_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    old_tenant_id VARCHAR(100) NULL,
    new_tenant_id VARCHAR(100) NULL,
    old_location_id VARCHAR(100) NULL,
    new_location_id VARCHAR(100) NULL,
    details VARCHAR(1000) NULL,
    actor_id VARCHAR(100) NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_machine_history_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
      ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_machine_history_machine_time (machine_id, occurred_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS machine_telemetry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(100) NOT NULL,
    machine_id BIGINT NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    location_id VARCHAR(100) NOT NULL,
    device_identity VARCHAR(150) NOT NULL,
    command_id VARCHAR(100) NULL,
    slot_id BIGINT NULL,
    perfume_level_percent DECIMAL(5,2) NULL,
    perfume_weight_grams DECIMAL(10,3) NULL,
    door_open BOOLEAN NULL,
    leak_detected BOOLEAN NULL,
    actuator_status VARCHAR(30) NULL,
    sensor_status VARCHAR(30) NULL,
    power_status VARCHAR(30) NULL,
    firmware_version VARCHAR(100) NULL,
    configuration_version VARCHAR(100) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_machine_telemetry_event UNIQUE (event_id),
    CONSTRAINT fk_machine_telemetry_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
      ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_telemetry_machine_time (machine_id, occurred_at),
    INDEX idx_telemetry_tenant_time (tenant_id, occurred_at),
    INDEX idx_telemetry_occurred_at (occurred_at),
    CONSTRAINT chk_telemetry_perfume_level CHECK (
      perfume_level_percent IS NULL OR perfume_level_percent BETWEEN 0 AND 100),
    CONSTRAINT chk_telemetry_perfume_weight CHECK (
      perfume_weight_grams IS NULL OR perfume_weight_grams >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS machine_device_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(100) NOT NULL,
    machine_id BIGINT NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    location_id VARCHAR(100) NOT NULL,
    device_identity VARCHAR(150) NOT NULL,
    command_id VARCHAR(100) NULL,
    event_type VARCHAR(40) NOT NULL,
    details VARCHAR(1000) NULL,
    firmware_version VARCHAR(100) NULL,
    configuration_version VARCHAR(100) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_machine_device_event UNIQUE (event_id),
    CONSTRAINT fk_machine_device_event_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
      ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_device_event_machine_time (machine_id, occurred_at),
    INDEX idx_device_event_tenant_time (tenant_id, occurred_at),
    INDEX idx_device_event_occurred_at (occurred_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS machine_alerts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dedupe_key VARCHAR(250) NOT NULL,
    alert_type VARCHAR(40) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    alert_source VARCHAR(20) NOT NULL DEFAULT 'DEVICE',
    tenant_id VARCHAR(100) NOT NULL,
    machine_id BIGINT NOT NULL,
    location_id VARCHAR(100) NOT NULL,
    slot_id BIGINT NULL,
    summary VARCHAR(500) NOT NULL,
    first_seen_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    acknowledged_at TIMESTAMP(6) NULL,
    acknowledged_by VARCHAR(100) NULL,
    assigned_at TIMESTAMP(6) NULL,
    assigned_to VARCHAR(100) NULL,
    resolved_at TIMESTAMP(6) NULL,
    resolved_by VARCHAR(100) NULL,
    resolution_notes VARCHAR(1000) NULL,
    escalated_at TIMESTAMP(6) NULL,
    reopen_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_machine_alert_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
      ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_alert_tenant_status_time (tenant_id, status, last_seen_at),
    INDEX idx_alert_machine_status (machine_id, status),
    INDEX idx_alert_dedupe_key (dedupe_key, status),
    CONSTRAINT chk_alert_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_alert_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'ASSIGNED', 'ESCALATED', 'RESOLVED')),
    CONSTRAINT chk_alert_source CHECK (alert_source IN ('DEVICE', 'MONITOR', 'INVENTORY')),
    CONSTRAINT chk_alert_reopen_count CHECK (reopen_count >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS machine_commands (
    id BIGINT NOT NULL AUTO_INCREMENT,
    command_id VARCHAR(100) NOT NULL,
    machine_id BIGINT NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    device_identity VARCHAR(150) NOT NULL,
    command_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    issued_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    acknowledged_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    result_success BOOLEAN NULL,
    result_details VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_machine_command_id UNIQUE (command_id),
    CONSTRAINT fk_machine_command_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
      ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_command_machine_issued (machine_id, issued_at),
    INDEX idx_command_status_expiry (status, expires_at),
    CONSTRAINT chk_machine_command_status
      CHECK (status IN ('PENDING', 'ACKNOWLEDGED', 'SUCCEEDED', 'FAILED', 'EXPIRED')),
    CONSTRAINT chk_machine_command_expiry CHECK (expires_at > issued_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS machine_message_receipts (
    event_id VARCHAR(100) NOT NULL,
    machine_id BIGINT NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (event_id),
    CONSTRAINT fk_message_receipt_machine FOREIGN KEY (machine_id) REFERENCES machines(id)
      ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_message_receipt_machine_time (machine_id, received_at),
    CONSTRAINT chk_message_receipt_type CHECK (message_type IN ('HEARTBEAT', 'TELEMETRY', 'EVENT'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS machine_locations (
  location_id VARCHAR(100) PRIMARY KEY,
  tenant_id VARCHAR(100) NOT NULL,
  name VARCHAR(150) NOT NULL,
  address VARCHAR(500) NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  INDEX idx_location_tenant (tenant_id)
);

SHOW DATABASES LIKE 'scentstation_machine';
