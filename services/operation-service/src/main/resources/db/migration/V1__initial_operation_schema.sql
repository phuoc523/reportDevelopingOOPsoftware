CREATE TABLE inventory_batches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    batch_code VARCHAR(100) NOT NULL,
    received_quantity INT NOT NULL,
    received_at DATE NOT NULL,
    expiry_date DATE NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_inventory_batches PRIMARY KEY (id),
    CONSTRAINT uk_inventory_batch_tenant_code UNIQUE (tenant_id, batch_code),
    CONSTRAINT uk_inventory_batches_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_inventory_batch_received_quantity CHECK (received_quantity > 0),
    CONSTRAINT chk_inventory_batch_dates CHECK (expiry_date IS NULL OR expiry_date >= received_at),
    INDEX idx_inventory_batches_tenant_product (tenant_id, product_id)
) ENGINE = InnoDB;

CREATE TABLE bottles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    bottle_code VARCHAR(100) NOT NULL,
    initial_quantity DECIMAL(12, 3) NOT NULL,
    current_quantity DECIMAL(12, 3) NOT NULL,
    calibrated_consumption_per_spray DECIMAL(12, 3) NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_bottles PRIMARY KEY (id),
    CONSTRAINT uk_bottle_tenant_code UNIQUE (tenant_id, bottle_code),
    CONSTRAINT uk_bottles_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_bottle_initial_quantity CHECK (initial_quantity > 0),
    CONSTRAINT chk_bottle_current_quantity CHECK (current_quantity >= 0),
    CONSTRAINT chk_bottle_calibration CHECK (
        calibrated_consumption_per_spray IS NULL OR calibrated_consumption_per_spray > 0
    ),
    CONSTRAINT fk_bottles_batch_tenant FOREIGN KEY (tenant_id, batch_id)
        REFERENCES inventory_batches (tenant_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_bottles_tenant_batch (tenant_id, batch_id),
    INDEX idx_bottles_tenant_product (tenant_id, product_id),
    INDEX idx_bottles_tenant_status (tenant_id, status)
) ENGINE = InnoDB;

CREATE TABLE bottle_installations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    bottle_id BIGINT NOT NULL,
    machine_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    installed_by BIGINT NULL,
    installed_at DATETIME(6) NOT NULL,
    removed_by BIGINT NULL,
    removed_at DATETIME(6) NULL,
    quantity_before_installation DECIMAL(12, 3) NOT NULL,
    quantity_after_removal DECIMAL(12, 3) NULL,
    active BOOLEAN NULL,
    CONSTRAINT pk_bottle_installations PRIMARY KEY (id),
    CONSTRAINT uk_active_installation_slot UNIQUE (tenant_id, machine_id, slot_id, active),
    CONSTRAINT uk_active_installation_bottle UNIQUE (tenant_id, bottle_id, active),
    CONSTRAINT chk_installation_quantity_before CHECK (quantity_before_installation >= 0),
    CONSTRAINT chk_installation_quantity_after CHECK (
        quantity_after_removal IS NULL OR quantity_after_removal >= 0
    ),
    CONSTRAINT chk_installation_active CHECK (active IS NULL OR active = TRUE),
    CONSTRAINT fk_installations_bottle_tenant FOREIGN KEY (tenant_id, bottle_id)
        REFERENCES bottles (tenant_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_installations_tenant_bottle_time (tenant_id, bottle_id, installed_at),
    INDEX idx_installations_tenant_machine_time (tenant_id, machine_id, installed_at)
) ENGINE = InnoDB;

CREATE TABLE dispense_consumptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    command_id VARCHAR(150) NOT NULL,
    bottle_id BIGINT NOT NULL,
    machine_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    dispense_type VARCHAR(30) NOT NULL,
    result VARCHAR(30) NOT NULL,
    consumed_quantity DECIMAL(12, 3) NOT NULL,
    inventory_deducted BOOLEAN NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    quantity_before DECIMAL(12, 3) NULL,
    quantity_after DECIMAL(12, 3) NULL,
    CONSTRAINT pk_dispense_consumptions PRIMARY KEY (id),
    CONSTRAINT uk_dispense_tenant_command UNIQUE (tenant_id, command_id),
    CONSTRAINT chk_dispense_consumed_quantity CHECK (consumed_quantity > 0),
    CONSTRAINT chk_dispense_quantity_before CHECK (quantity_before IS NULL OR quantity_before >= 0),
    CONSTRAINT chk_dispense_quantity_after CHECK (quantity_after IS NULL OR quantity_after >= 0),
    CONSTRAINT fk_dispense_bottle_tenant FOREIGN KEY (tenant_id, bottle_id)
        REFERENCES bottles (tenant_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_dispense_tenant_bottle_time (tenant_id, bottle_id, processed_at),
    INDEX idx_dispense_tenant_machine_time (tenant_id, machine_id, processed_at)
) ENGINE = InnoDB;

CREATE TABLE bottle_movements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    bottle_id BIGINT NOT NULL,
    movement_type VARCHAR(30) NOT NULL,
    machine_id BIGINT NULL,
    slot_id BIGINT NULL,
    quantity_before DECIMAL(12, 3) NULL,
    quantity_after DECIMAL(12, 3) NULL,
    actor_id BIGINT NULL,
    reason VARCHAR(500) NULL,
    reference_id VARCHAR(150) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_bottle_movements PRIMARY KEY (id),
    CONSTRAINT chk_movement_quantity_before CHECK (quantity_before IS NULL OR quantity_before >= 0),
    CONSTRAINT chk_movement_quantity_after CHECK (quantity_after IS NULL OR quantity_after >= 0),
    CONSTRAINT fk_movements_bottle_tenant FOREIGN KEY (tenant_id, bottle_id)
        REFERENCES bottles (tenant_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_movements_tenant_time (tenant_id, created_at),
    INDEX idx_movements_tenant_bottle_time (tenant_id, bottle_id, created_at),
    INDEX idx_movements_tenant_machine_time (tenant_id, machine_id, created_at),
    INDEX idx_movements_tenant_type_time (tenant_id, movement_type, created_at)
) ENGINE = InnoDB;

CREATE TABLE inventory_adjustments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    bottle_id BIGINT NOT NULL,
    quantity_before DECIMAL(12, 3) NOT NULL,
    quantity_after DECIMAL(12, 3) NOT NULL,
    actor_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_inventory_adjustments PRIMARY KEY (id),
    CONSTRAINT chk_adjustment_quantity_before CHECK (quantity_before >= 0),
    CONSTRAINT chk_adjustment_quantity_after CHECK (quantity_after >= 0),
    CONSTRAINT fk_adjustments_bottle_tenant FOREIGN KEY (tenant_id, bottle_id)
        REFERENCES bottles (tenant_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_adjustments_tenant_bottle_time (tenant_id, bottle_id, created_at),
    INDEX idx_adjustments_tenant_time (tenant_id, created_at)
) ENGINE = InnoDB;

CREATE TABLE sensor_reconciliations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    sensor_event_id VARCHAR(150) NOT NULL,
    bottle_id BIGINT NOT NULL,
    machine_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    estimated_remaining_sprays BIGINT NULL,
    predicted_quantity DECIMAL(12, 3) NOT NULL,
    sensor_quantity DECIMAL(12, 3) NOT NULL,
    discrepancy DECIMAL(12, 3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    low_stock BOOLEAN NOT NULL,
    measured_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_sensor_reconciliations PRIMARY KEY (id),
    CONSTRAINT uk_reconciliation_tenant_event UNIQUE (tenant_id, sensor_event_id),
    CONSTRAINT chk_reconciliation_estimated_sprays CHECK (
        estimated_remaining_sprays IS NULL OR estimated_remaining_sprays >= 0
    ),
    CONSTRAINT chk_reconciliation_predicted CHECK (predicted_quantity >= 0),
    CONSTRAINT chk_reconciliation_sensor CHECK (sensor_quantity >= 0),
    CONSTRAINT chk_reconciliation_discrepancy CHECK (discrepancy >= 0),
    CONSTRAINT fk_reconciliations_bottle_tenant FOREIGN KEY (tenant_id, bottle_id)
        REFERENCES bottles (tenant_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_reconciliations_tenant_bottle_time (tenant_id, bottle_id, processed_at),
    INDEX idx_reconciliations_tenant_machine_time (tenant_id, machine_id, processed_at)
) ENGINE = InnoDB;

CREATE TABLE inventory_audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    actor_id BIGINT NULL,
    action VARCHAR(60) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NULL,
    machine_id BIGINT NULL,
    slot_id BIGINT NULL,
    before_value VARCHAR(1000) NULL,
    after_value VARCHAR(1000) NULL,
    reason VARCHAR(500) NULL,
    reference_id VARCHAR(150) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_inventory_audit_events PRIMARY KEY (id),
    INDEX idx_inventory_audit_tenant_time (tenant_id, created_at),
    INDEX idx_inventory_audit_entity_time (tenant_id, entity_type, entity_id, created_at),
    INDEX idx_inventory_audit_action_time (tenant_id, action, created_at)
) ENGINE = InnoDB;

CREATE TABLE maintenance_tickets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    machine_id BIGINT NOT NULL,
    slot_id BIGINT NULL,
    bottle_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    category VARCHAR(100) NOT NULL,
    severity VARCHAR(30) NOT NULL,
    priority VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reported_by_actor_id BIGINT NOT NULL,
    assigned_to_actor_id BIGINT NULL,
    due_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    closed_at DATETIME(6) NULL,
    location_id BIGINT NULL,
    critical BOOLEAN NOT NULL,
    CONSTRAINT pk_maintenance_tickets PRIMARY KEY (id),
    CONSTRAINT uk_maintenance_tickets_tenant_id UNIQUE (tenant_id, id),
    INDEX idx_maintenance_tenant_status (tenant_id, status),
    INDEX idx_maintenance_tenant_machine (tenant_id, machine_id),
    INDEX idx_maintenance_tenant_assignee (tenant_id, assigned_to_actor_id),
    INDEX idx_maintenance_tenant_title (tenant_id, title)
) ENGINE = InnoDB;

CREATE TABLE maintenance_assignment_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    previous_technician_actor_id BIGINT NULL,
    technician_actor_id BIGINT NOT NULL,
    performed_by_actor_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_maintenance_assignment_history PRIMARY KEY (id),
    CONSTRAINT fk_assignment_ticket_tenant FOREIGN KEY (tenant_id, ticket_id)
        REFERENCES maintenance_tickets (tenant_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_maintenance_assignment_ticket (tenant_id, ticket_id, created_at, id),
    INDEX idx_maintenance_assignment_technician (tenant_id, technician_actor_id, created_at)
) ENGINE = InnoDB;

CREATE TABLE maintenance_checklist_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NULL,
    required_item BOOLEAN NOT NULL,
    completed BOOLEAN NOT NULL,
    result_note VARCHAR(2000) NULL,
    completed_by_actor_id BIGINT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_maintenance_checklist_items PRIMARY KEY (id),
    CONSTRAINT fk_checklist_ticket_tenant FOREIGN KEY (tenant_id, ticket_id)
        REFERENCES maintenance_tickets (tenant_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_maintenance_checklist_ticket (tenant_id, ticket_id, id),
    INDEX idx_maintenance_checklist_incomplete (tenant_id, ticket_id, required_item, completed)
) ENGINE = InnoDB;

CREATE TABLE maintenance_repair_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    diagnosed_by_actor_id BIGINT NOT NULL,
    diagnostic_result VARCHAR(2000) NOT NULL,
    corrective_action VARCHAR(2000) NOT NULL,
    replaced_part VARCHAR(500) NULL,
    cost DECIMAL(15, 2) NULL,
    repair_result VARCHAR(30) NOT NULL,
    note VARCHAR(2000) NULL,
    evidence_url VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_maintenance_repair_logs PRIMARY KEY (id),
    CONSTRAINT chk_maintenance_repair_cost CHECK (cost IS NULL OR cost >= 0),
    CONSTRAINT fk_repair_ticket_tenant FOREIGN KEY (tenant_id, ticket_id)
        REFERENCES maintenance_tickets (tenant_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_repair_log_ticket (tenant_id, ticket_id, created_at, id)
) ENGINE = InnoDB;

CREATE TABLE post_maintenance_tests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    machine_id BIGINT NOT NULL,
    requested_by_actor_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    external_test_id VARCHAR(150) NULL,
    result_message VARCHAR(2000) NULL,
    requested_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    CONSTRAINT pk_post_maintenance_tests PRIMARY KEY (id),
    CONSTRAINT uk_post_maintenance_test_external UNIQUE (tenant_id, external_test_id),
    CONSTRAINT fk_post_test_ticket_tenant FOREIGN KEY (tenant_id, ticket_id)
        REFERENCES maintenance_tickets (tenant_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_post_maintenance_test_ticket (tenant_id, ticket_id, requested_at, id)
) ENGINE = InnoDB;

CREATE TABLE maintenance_alert_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    source_alert_id VARCHAR(150) NOT NULL,
    alert_type VARCHAR(100) NOT NULL,
    ticket_id BIGINT NOT NULL,
    machine_id BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_maintenance_alert_links PRIMARY KEY (id),
    CONSTRAINT uk_maintenance_alert_tenant_alert UNIQUE (tenant_id, source_alert_id),
    CONSTRAINT fk_alert_ticket_tenant FOREIGN KEY (tenant_id, ticket_id)
        REFERENCES maintenance_tickets (tenant_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_maintenance_alert_ticket (tenant_id, ticket_id, id)
) ENGINE = InnoDB;

CREATE TABLE maintenance_audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    action VARCHAR(60) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NULL,
    actor_id BIGINT NULL,
    ticket_id BIGINT NULL,
    machine_id BIGINT NULL,
    before_state VARCHAR(2000) NULL,
    after_state VARCHAR(2000) NULL,
    source VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_maintenance_audit_events PRIMARY KEY (id),
    CONSTRAINT fk_maintenance_audit_ticket_tenant FOREIGN KEY (tenant_id, ticket_id)
        REFERENCES maintenance_tickets (tenant_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_maintenance_audit_ticket_time (tenant_id, ticket_id, created_at, id)
) ENGINE = InnoDB;
