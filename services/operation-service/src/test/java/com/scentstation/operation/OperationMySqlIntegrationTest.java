package com.scentstation.operation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OperationMySqlIntegrationTest {

    private static final Set<String> OPERATION_TABLES = Set.of(
            "inventory_batches",
            "bottles",
            "bottle_installations",
            "dispense_consumptions",
            "bottle_movements",
            "inventory_adjustments",
            "sensor_reconciliations",
            "inventory_audit_events",
            "maintenance_tickets",
            "maintenance_assignment_history",
            "maintenance_checklist_items",
            "maintenance_repair_logs",
            "post_maintenance_tests",
            "maintenance_alert_links",
            "maintenance_audit_events"
    );

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.41")
            .withDatabaseName("scentstation_operation")
            .withUsername("operation")
            .withPassword("operation");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void flywayBootstrapsAndAllFifteenTablesAreWritable() {
        assertEquals(1, jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '1'",
                Integer.class
        ));

        Set<String> actualTables = Set.copyOf(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class
        ));
        assertTrue(actualTables.containsAll(OPERATION_TABLES));

        long batchId = insertBatch(100L, "ALL-BATCH");
        long bottleId = insertBottle(100L, batchId, "ALL-BOTTLE");
        insertInstallation(100L, bottleId, 1001L, 1002L, true);
        jdbc.update("""
                INSERT INTO dispense_consumptions
                    (tenant_id, command_id, bottle_id, machine_id, slot_id, dispense_type, result,
                     consumed_quantity, inventory_deducted, processed_at, quantity_before, quantity_after)
                VALUES (?, 'ALL-COMMAND', ?, 1001, 1002, 'CUSTOMER', 'SUCCESS',
                        1.000, TRUE, CURRENT_TIMESTAMP(6), 10.000, 9.000)
                """, 100L, bottleId);
        jdbc.update("""
                INSERT INTO bottle_movements
                    (tenant_id, bottle_id, movement_type, machine_id, slot_id, quantity_before,
                     quantity_after, actor_id, reason, created_at)
                VALUES (?, ?, 'INSTALL', 1001, 1002, 10.000, 10.000, 1003, 'test', CURRENT_TIMESTAMP(6))
                """, 100L, bottleId);
        jdbc.update("""
                INSERT INTO inventory_adjustments
                    (tenant_id, bottle_id, quantity_before, quantity_after, actor_id, reason, created_at)
                VALUES (?, ?, 10.000, 9.000, 1003, 'test', CURRENT_TIMESTAMP(6))
                """, 100L, bottleId);
        jdbc.update("""
                INSERT INTO sensor_reconciliations
                    (tenant_id, sensor_event_id, bottle_id, machine_id, slot_id, estimated_remaining_sprays,
                     predicted_quantity, sensor_quantity, discrepancy, status, low_stock, measured_at, processed_at)
                VALUES (?, 'ALL-SENSOR', ?, 1001, 1002, 90, 9.000, 9.000, 0.000,
                        'NORMAL', FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, 100L, bottleId);
        jdbc.update("""
                INSERT INTO inventory_audit_events
                    (tenant_id, actor_id, action, entity_type, entity_id, created_at)
                VALUES (?, 1003, 'BATCH_CREATED', 'BATCH', ?, CURRENT_TIMESTAMP(6))
                """, 100L, batchId);

        long ticketId = insertTicket(100L, bottleId, "ALL-TICKET");
        jdbc.update("""
                INSERT INTO maintenance_assignment_history
                    (tenant_id, ticket_id, technician_actor_id, performed_by_actor_id, event_type, created_at)
                VALUES (?, ?, 1004, 1003, 'ASSIGNED', CURRENT_TIMESTAMP(6))
                """, 100L, ticketId);
        jdbc.update("""
                INSERT INTO maintenance_checklist_items
                    (tenant_id, ticket_id, item_name, required_item, completed, created_at)
                VALUES (?, ?, 'Inspect', TRUE, FALSE, CURRENT_TIMESTAMP(6))
                """, 100L, ticketId);
        jdbc.update("""
                INSERT INTO maintenance_repair_logs
                    (tenant_id, ticket_id, diagnosed_by_actor_id, diagnostic_result, corrective_action,
                     cost, repair_result, created_at)
                VALUES (?, ?, 1004, 'diagnosed', 'repaired', 10.00, 'SUCCESS', CURRENT_TIMESTAMP(6))
                """, 100L, ticketId);
        insertPostTest(100L, ticketId, "ALL-EXTERNAL-TEST");
        insertAlert(100L, ticketId, "ALL-ALERT");
        jdbc.update("""
                INSERT INTO maintenance_audit_events
                    (tenant_id, action, entity_type, entity_id, actor_id, ticket_id, machine_id, source, created_at)
                VALUES (?, 'TICKET_CREATED', 'TICKET', ?, 1003, ?, 1001, 'MAINTENANCE', CURRENT_TIMESTAMP(6))
                """, 100L, ticketId, ticketId);

        for (String table : OPERATION_TABLES) {
            assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class) > 0, table);
        }
    }

    @Test
    void tenantSafeForeignKeysRejectMissingAndCrossTenantParents() {
        long tenantABatch = insertBatch(200L, "FK-BATCH-A");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertBottle(200L, tenantABatch + 9999, "FK-MISSING-BATCH"));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertBottle(201L, tenantABatch, "FK-CROSS-BATCH"));

        long tenantABottle = insertBottle(200L, tenantABatch, "FK-BOTTLE-A");
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO inventory_adjustments
                    (tenant_id, bottle_id, quantity_before, quantity_after, actor_id, reason, created_at)
                VALUES (201, ?, 10.000, 9.000, 999999, 'cross tenant', CURRENT_TIMESTAMP(6))
                """, tenantABottle));

        long tenantATicket = insertTicket(200L, null, "FK-TICKET-A");
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO maintenance_checklist_items
                    (tenant_id, ticket_id, item_name, required_item, completed, created_at)
                VALUES (201, ?, 'cross tenant', TRUE, FALSE, CURRENT_TIMESTAMP(6))
                """, tenantATicket));

        assertEquals(999999L, jdbc.queryForObject(
                "SELECT product_id FROM bottles WHERE id = ?", Long.class, tenantABottle));
        assertEquals(900001L, jdbc.queryForObject(
                "SELECT machine_id FROM maintenance_tickets WHERE id = ?", Long.class, tenantATicket));
    }

    @Test
    void activeInstallationUniquenessUsesMySqlNullSemantics() {
        long batchId = insertBatch(300L, "ACTIVE-BATCH");
        long firstBottle = insertBottle(300L, batchId, "ACTIVE-FIRST");
        long secondBottle = insertBottle(300L, batchId, "ACTIVE-SECOND");

        insertInstallation(300L, firstBottle, 3001L, 3002L, true);
        assertThrows(DataIntegrityViolationException.class,
                () -> insertInstallation(300L, secondBottle, 3001L, 3002L, true));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertInstallation(300L, firstBottle, 3001L, 3003L, true));

        removeActiveInstallation(300L, firstBottle);
        insertInstallation(300L, secondBottle, 3001L, 3002L, true);
        removeActiveInstallation(300L, secondBottle);
        insertInstallation(300L, firstBottle, 3001L, 3002L, true);
        removeActiveInstallation(300L, firstBottle);

        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM bottle_installations WHERE tenant_id = 300", Integer.class));
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM bottle_installations WHERE tenant_id = 300 AND active IS NULL",
                Integer.class));
    }

    @Test
    void databasePreservesUniqueAndIdempotencyRules() {
        long batchId = insertBatch(400L, "UNIQUE-BATCH");
        assertThrows(DataIntegrityViolationException.class,
                () -> insertBatch(400L, "UNIQUE-BATCH"));

        long bottleId = insertBottle(400L, batchId, "UNIQUE-BOTTLE");
        assertThrows(DataIntegrityViolationException.class,
                () -> insertBottle(400L, batchId, "UNIQUE-BOTTLE"));

        insertDispense(400L, bottleId, "UNIQUE-COMMAND");
        assertThrows(DataIntegrityViolationException.class,
                () -> insertDispense(400L, bottleId, "UNIQUE-COMMAND"));

        insertReconciliation(400L, bottleId, "UNIQUE-SENSOR");
        assertThrows(DataIntegrityViolationException.class,
                () -> insertReconciliation(400L, bottleId, "UNIQUE-SENSOR"));

        long ticketId = insertTicket(400L, bottleId, "UNIQUE-TICKET");
        insertPostTest(400L, ticketId, null);
        insertPostTest(400L, ticketId, null);
        insertPostTest(400L, ticketId, "UNIQUE-EXTERNAL-TEST");
        assertThrows(DataIntegrityViolationException.class,
                () -> insertPostTest(400L, ticketId, "UNIQUE-EXTERNAL-TEST"));

        insertAlert(400L, ticketId, "UNIQUE-ALERT");
        assertThrows(DataIntegrityViolationException.class,
                () -> insertAlert(400L, ticketId, "UNIQUE-ALERT"));
    }

    @Test
    void databaseRejectsInvalidQuantityAndMoney() {
        long batchId = insertBatch(500L, "CHECK-BATCH");
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO bottles
                    (tenant_id, product_id, batch_id, bottle_code, initial_quantity, current_quantity,
                     status, created_at, updated_at)
                VALUES (500, 999999, ?, 'CHECK-BOTTLE', 10.000, -1.000,
                        'AVAILABLE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, batchId));

        long ticketId = insertTicket(500L, null, "CHECK-TICKET");
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO maintenance_repair_logs
                    (tenant_id, ticket_id, diagnosed_by_actor_id, diagnostic_result, corrective_action,
                     cost, repair_result, created_at)
                VALUES (500, ?, 999999, 'diagnosed', 'repaired', -0.01,
                        'SUCCESS', CURRENT_TIMESTAMP(6))
                """, ticketId));
    }

    @Test
    void transactionRollbackRemainsEffectiveOnMySql() {
        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            insertBatch(600L, "ROLLBACK-BATCH");
            throw new IllegalStateException("force rollback");
        }));

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_batches WHERE tenant_id = 600",
                Integer.class
        ));
    }

    private long insertBatch(long tenantId, String code) {
        jdbc.update("""
                INSERT INTO inventory_batches
                    (tenant_id, product_id, batch_code, received_quantity, received_at,
                     expiry_date, created_at, updated_at)
                VALUES (?, 999999, ?, 10, CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 1 YEAR),
                        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, tenantId, code);
        return jdbc.queryForObject(
                "SELECT id FROM inventory_batches WHERE tenant_id = ? AND batch_code = ?",
                Long.class, tenantId, code
        );
    }

    private long insertBottle(long tenantId, long batchId, String code) {
        jdbc.update("""
                INSERT INTO bottles
                    (tenant_id, product_id, batch_id, bottle_code, initial_quantity, current_quantity,
                     status, created_at, updated_at)
                VALUES (?, 999999, ?, ?, 10.000, 10.000, 'AVAILABLE',
                        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, tenantId, batchId, code);
        return jdbc.queryForObject(
                "SELECT id FROM bottles WHERE tenant_id = ? AND bottle_code = ?",
                Long.class, tenantId, code
        );
    }

    private void insertInstallation(
            long tenantId,
            long bottleId,
            long machineId,
            long slotId,
            Boolean active
    ) {
        jdbc.update("""
                INSERT INTO bottle_installations
                    (tenant_id, bottle_id, machine_id, slot_id, installed_by, installed_at,
                     quantity_before_installation, active)
                VALUES (?, ?, ?, ?, 999999, CURRENT_TIMESTAMP(6), 10.000, ?)
                """, tenantId, bottleId, machineId, slotId, active);
    }

    private void removeActiveInstallation(long tenantId, long bottleId) {
        jdbc.update("""
                UPDATE bottle_installations
                SET active = NULL, removed_by = 999999, removed_at = CURRENT_TIMESTAMP(6),
                    quantity_after_removal = 10.000
                WHERE tenant_id = ? AND bottle_id = ? AND active = TRUE
                """, tenantId, bottleId);
    }

    private void insertDispense(long tenantId, long bottleId, String commandId) {
        jdbc.update("""
                INSERT INTO dispense_consumptions
                    (tenant_id, command_id, bottle_id, machine_id, slot_id, dispense_type, result,
                     consumed_quantity, inventory_deducted, processed_at)
                VALUES (?, ?, ?, 999999, 999998, 'CUSTOMER', 'SUCCESS',
                        1.000, TRUE, CURRENT_TIMESTAMP(6))
                """, tenantId, commandId, bottleId);
    }

    private void insertReconciliation(long tenantId, long bottleId, String eventId) {
        jdbc.update("""
                INSERT INTO sensor_reconciliations
                    (tenant_id, sensor_event_id, bottle_id, machine_id, slot_id, predicted_quantity,
                     sensor_quantity, discrepancy, status, low_stock, measured_at, processed_at)
                VALUES (?, ?, ?, 999999, 999998, 10.000, 10.000, 0.000,
                        'NORMAL', FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, tenantId, eventId, bottleId);
    }

    private long insertTicket(long tenantId, Long bottleId, String title) {
        jdbc.update("""
                INSERT INTO maintenance_tickets
                    (tenant_id, machine_id, bottle_id, title, description, category, severity,
                     priority, status, reported_by_actor_id, created_at, updated_at, critical)
                VALUES (?, 900001, ?, ?, 'test', 'GENERAL', 'MODERATE', 'MEDIUM', 'OPEN',
                        900002, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), FALSE)
                """, tenantId, bottleId, title);
        return jdbc.queryForObject(
                "SELECT id FROM maintenance_tickets WHERE tenant_id = ? AND title = ?",
                Long.class, tenantId, title
        );
    }

    private void insertPostTest(long tenantId, long ticketId, String externalId) {
        jdbc.update("""
                INSERT INTO post_maintenance_tests
                    (tenant_id, ticket_id, machine_id, requested_by_actor_id, status,
                     external_test_id, requested_at)
                VALUES (?, ?, 900001, 900002, 'REQUESTED', ?, CURRENT_TIMESTAMP(6))
                """, tenantId, ticketId, externalId);
    }

    private void insertAlert(long tenantId, long ticketId, String sourceAlertId) {
        jdbc.update("""
                INSERT INTO maintenance_alert_links
                    (tenant_id, source_alert_id, alert_type, ticket_id, machine_id, occurred_at, created_at)
                VALUES (?, ?, 'CRITICAL', ?, 900001, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, tenantId, sourceAlertId, ticketId);
    }
}
