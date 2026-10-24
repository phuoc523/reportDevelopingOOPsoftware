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
