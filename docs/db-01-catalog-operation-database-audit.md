# DB-01 Catalog + Operation Database Audit

## 1. Objective

Audit thiết kế database hiện tại của `catalog-service` và `operation-service` (Inventory + Maintenance), dựa trên source/test/config thực tế tại HEAD. Đây chỉ là tài liệu thiết kế và readiness assessment; không tạo SQL, migration hay thay đổi runtime code.

## 2. Git / Branch

- Current branch: `feature/maintenance`
- HEAD khi audit: `3ddd61880d6262703350f9de30fead979dfd1b0c`
- Source/test/config modification by DB-01: **NONE**.
- Commit/push/merge/rebase/checkout by DB-01: **NONE**.
- Worktree đã có sẵn các thay đổi không thuộc DB-01 (ví dụ `.gitignore`, các `.gitkeep` và nhiều tài liệu untracked); audit không sửa hoặc revert chúng.

## 3. Scope inspected

- Catalog: toàn bộ `src/main/java`, `src/main/resources`, `src/test`, `pom.xml`, `Dockerfile`.
- Operation: toàn bộ `src/main/java`, `src/main/resources`, `src/test`, `pom.xml`, `Dockerfile`; tách Inventory và Maintenance.
- Read-only convention context: `.env.example`, `docker-compose.yml`, `README.md`, `CONTRIBUTING.md`, `docs-assignment-notes.md`.
- Không audit source của service/member khác. Mọi ID ngoài boundary được xem là scalar reference.

## 4. Current persistence architecture

### Catalog

- Spring Boot 3.4.5, Java 21, Spring Data JPA, MySQL runtime, H2 test.
- 1 JPA entity/1 table: `Product` / `products`.
- ID dùng `Long` + `GenerationType.IDENTITY`.
- Không có JPA relationship, FK, index hoặc unique constraint khai báo.
- Timestamp do callback ứng dụng (`@PrePersist`, `@PreUpdate`) tạo; không có DB default.
- `ProductRepository` chỉ kế thừa `JpaRepository`; business service dùng `findAll()` và `findById()` không có tenant predicate.

### Operation

- Spring Boot 3.4.5, Java 21, Spring Data JPA, MySQL runtime, H2 test.
- 15 JPA entity/15 table: Inventory 8, Maintenance 7.
- Tất cả PK là `Long` + `IDENTITY`.
- Không có object relationship (`@ManyToOne`, `@OneToMany`, `@JoinColumn`) và không có FK annotation/schema. Quan hệ nội bộ hiện chỉ là scalar IDs.
- Unique/index annotations tập trung ở idempotency/active installation và Maintenance query paths; phần lớn Inventory history query chưa có index.
- Timestamps chủ yếu do `@PrePersist`/`@PreUpdate`; lifecycle timestamps còn lại do service set.

## 5. Catalog entity inventory

Entity metadata: `Product` có `@Entity`, `@Table(name="products")`; PK `id BIGINT AUTO_INCREMENT`; không có declared index/unique/FK/enum.

| Field / column | Java → expected MySQL | Nullable | Length / precision | Unique / index | Behavior / reference |
|---|---|---:|---|---|---|
| `id` / `id` | `Long` → `BIGINT` | NO | — | PK | `IDENTITY` |
| `tenantId` / implicit `tenant_id` | `Long` → `BIGINT` | NO | — | none | External Identity/tenant scalar |
| `name` / `name` | `String` → `VARCHAR(255)` | NO | default 255 | none | No business-key constraint |
| `description` / `description` | `String` → `VARCHAR(1000)` | YES | 1000 | none | — |
| `fragranceNotes` / implicit `fragrance_notes` | `String` → `VARCHAR(500)` | YES | 500 | none | — |
| `imageUrl` / implicit `image_url` | `String` → `VARCHAR(255)` | YES | default 255 | none | — |
| `defaultPrice` / implicit `default_price` | `BigDecimal` → `DECIMAL(12,2)` | NO | 12,2 | none | Money |
| `available` / `available` | `Boolean` → `BOOLEAN/TINYINT(1)` | NO | — | none | Java default `true` |
| `createdAt` / implicit `created_at` | `LocalDateTime` → `DATETIME(6)` | NO | — | none | app `@PrePersist`, immutable |
| `updatedAt` / implicit `updated_at` | `LocalDateTime` → `DATETIME(6)` | NO | — | none | app create + `@PreUpdate` |

Tên cột “implicit” phụ thuộc Spring/Hibernate physical naming strategy; V1 migration phải chốt tên snake_case đúng như mapping runtime hiện tại.

## 6. Catalog proposed physical schema map

Chỉ là schema map đề xuất, không phải SQL.

```text
scentstation_catalog
└── products
    ├── id BIGINT PK AUTO_INCREMENT
    ├── tenant_id BIGINT NOT NULL                     [external tenant ref; NO FK]
    ├── name VARCHAR(255) NOT NULL
    ├── description VARCHAR(1000) NULL
    ├── fragrance_notes VARCHAR(500) NULL
    ├── image_url VARCHAR(255) NULL
    ├── default_price DECIMAL(12,2) NOT NULL
    ├── available BOOLEAN NOT NULL
    ├── created_at DATETIME(6) NOT NULL
    ├── updated_at DATETIME(6) NOT NULL
    ├── PRIMARY KEY (id)
    ├── UNIQUE CONSTRAINTS: none currently; candidate only after product business key is agreed
    ├── INDEXES: candidate (tenant_id, id), and tenant-scoped list/sort index after API query is fixed
    ├── FOREIGN KEYS: none
    ├── EXTERNAL REFERENCES: tenant_id
    └── CHECK candidates: default_price >= 0
```

## 7. Operation Inventory entity inventory

Mọi entity dưới đây có `@Entity`; mọi PK là `id BIGINT NOT NULL AUTO_INCREMENT`, `@Id`, `IDENTITY`. “Internal logical ref” nghĩa là cùng DB/service nhưng **hiện chưa có FK**. String không ghi length dùng `VARCHAR(255)`.

### `InventoryBatch` → `inventory_batches`

| Column | Java → SQL | Null | Constraint / behavior |
|---|---|---:|---|
| `tenant_id` | `Long` → `BIGINT` | NO | external tenant; UK part |
| `product_id` | `Long` → `BIGINT` | NO | external Catalog ref; NO FK |
| `batch_code` | `String` → `VARCHAR(100)` | NO | `uk_inventory_batch_tenant_code(tenant_id,batch_code)` |
| `received_quantity` | `Integer` → `INT` | NO | CHECK candidate `> 0` |
| `received_at` | `LocalDate` → `DATE` | NO | app supplied |
| `expiry_date` | `LocalDate` → `DATE` | YES | Java validates `expiry_date >= received_at` |
| `created_at` | `LocalDateTime` → `DATETIME(6)` | NO | `@PrePersist`, immutable |
| `updated_at` | `LocalDateTime` → `DATETIME(6)` | NO | `@PrePersist/@PreUpdate` |

### `Bottle` → `bottles`

| Column | Java → SQL | Null | Constraint / behavior |
|---|---|---:|---|
| `tenant_id` | `Long` → `BIGINT` | NO | external tenant; UK part |
| `product_id` | `Long` → `BIGINT` | NO | external Catalog ref; NO FK |
| `batch_id` | `Long` → `BIGINT` | NO | internal logical ref to `inventory_batches.id`; no FK |
| `bottle_code` | `String` → `VARCHAR(100)` | NO | `uk_bottle_tenant_code(tenant_id,bottle_code)` |
| `initial_quantity` | `BigDecimal` → `DECIMAL(12,3)` | NO | CHECK candidate `>= 0` |
| `current_quantity` | `BigDecimal` → `DECIMAL(12,3)` | NO | app defaults from initial; CHECK candidate `>= 0` |
| `calibrated_consumption_per_spray` | `BigDecimal` → `DECIMAL(12,3)` | YES | CHECK candidate `> 0` when non-null |
| `status` | `BottleStatus` → `VARCHAR(30)` | NO | `EnumType.STRING`; app default `AVAILABLE` |
| `created_at` | `LocalDateTime` → `DATETIME(6)` | NO | app generated, immutable |
| `updated_at` | `LocalDateTime` → `DATETIME(6)` | NO | app generated/updated |

### `BottleInstallation` → `bottle_installations`

| Column | Java → SQL | Null | Constraint / behavior |
|---|---|---:|---|
| `tenant_id` | `Long` → `BIGINT` | NO | external tenant; UK part |
| `bottle_id` | `Long` → `BIGINT` | NO | internal logical ref to `bottles`; no FK; UK part |
| `machine_id` | `Long` → `BIGINT` | NO | external Machine ref; NO FK; UK part |
| `slot_id` | `Long` → `BIGINT` | NO | external Machine ref; NO FK; UK part |
| `installed_by` | `Long` → `BIGINT` | YES | external actor ref |
| `installed_at` | `LocalDateTime` → `DATETIME(6)` | NO | app defaults at persist |
| `removed_by` | `Long` → `BIGINT` | YES | external actor ref |
| `removed_at` | `LocalDateTime` → `DATETIME(6)` | YES | service sets on removal |
| `quantity_before_installation` | `BigDecimal` → `DECIMAL(12,3)` | NO | CHECK candidate `>= 0` |
| `quantity_after_removal` | `BigDecimal` → `DECIMAL(12,3)` | YES | CHECK candidate `>= 0` |
| `active` | `Boolean` → `BOOLEAN/TINYINT(1)` | YES | active=`TRUE`; historical rows deliberately set `NULL` |

Declared unique constraints: `uk_active_installation_slot(tenant_id,machine_id,slot_id,active)` and `uk_active_installation_bottle(tenant_id,bottle_id,active)`. MySQL multiple-NULL semantics allow history rows while rejecting duplicate `TRUE`; H2 integration test covers duplicate active slot. This design must be verified against the exact MySQL version before migration.

### `DispenseConsumption` → `dispense_consumptions`

| Column | Java → SQL | Null | Constraint / behavior |
|---|---|---:|---|
| `tenant_id` | `Long` → `BIGINT` | NO | external tenant; UK part |
| `command_id` | `String` → `VARCHAR(150)` | NO | `uk_dispense_tenant_command(tenant_id,command_id)` idempotency |
| `bottle_id` | `Long` → `BIGINT` | NO | internal logical bottle ref; no FK |
| `machine_id`, `slot_id` | `Long` → `BIGINT` | NO | external Machine refs; NO FK |
| `dispense_type` | `DispenseType` → `VARCHAR(30)` | NO | STRING enum |
| `result` | `DispenseResult` → `VARCHAR(30)` | NO | STRING enum |
| `consumed_quantity` | `BigDecimal` → `DECIMAL(12,3)` | NO | CHECK candidate `> 0` |
| `inventory_deducted` | `Boolean` → `BOOLEAN/TINYINT(1)` | NO | app default `false` |
| `processed_at` | `LocalDateTime` → `DATETIME(6)` | NO | `@PrePersist`, immutable |
| `quantity_before`, `quantity_after` | `BigDecimal` → `DECIMAL(12,3)` | YES | app sets in current flow; nullable mapping |

### `BottleMovement` → `bottle_movements`

| Column | Java → SQL | Null | Constraint / behavior |
|---|---|---:|---|
| `tenant_id` | `Long` → `BIGINT` | NO | external tenant |
| `bottle_id` | `Long` → `BIGINT` | NO | internal logical bottle ref; no FK |
| `movement_type` | `BottleMovementType` → `VARCHAR(30)` | NO | STRING enum |
| `machine_id`, `slot_id` | `Long` → `BIGINT` | YES | external Machine refs |
| `quantity_before`, `quantity_after` | `BigDecimal` → `DECIMAL(12,3)` | YES | quantity snapshot |
| `actor_id` | `Long` → `BIGINT` | YES | external Identity ref |
| `reason` | `String` → `VARCHAR(500)` | YES | — |
| `reference_id` | `String` → `VARCHAR(150)` | YES | generic command/business reference |
| `created_at` | `LocalDateTime` → `DATETIME(6)` | NO | `@PrePersist`, immutable |

### `InventoryAdjustment` → `inventory_adjustments`

| Column | Java → SQL | Null | Constraint / behavior |
|---|---|---:|---|
| `tenant_id` | `Long` → `BIGINT` | NO | external tenant |
| `bottle_id` | `Long` → `BIGINT` | NO | internal logical bottle ref; no FK |
| `quantity_before`, `quantity_after` | `BigDecimal` → `DECIMAL(12,3)` | NO | CHECK candidates `>= 0` |
| `actor_id` | `Long` → `BIGINT` | NO | external Identity ref |
| `reason` | `String` → `VARCHAR(500)` | NO | — |
| `created_at` | `LocalDateTime` → `DATETIME(6)` | NO | `@PrePersist`, immutable |

### `SensorReconciliation` → `sensor_reconciliations`

| Column | Java → SQL | Null | Constraint / behavior |
|---|---|---:|---|
| `tenant_id` | `Long` → `BIGINT` | NO | external tenant; UK part |
| `sensor_event_id` | `String` → `VARCHAR(150)` | NO | `uk_reconciliation_tenant_event` idempotency |
| `bottle_id` | `Long` → `BIGINT` | NO | internal logical bottle ref; no FK |
| `machine_id`, `slot_id` | `Long` → `BIGINT` | NO | external Machine refs |
| `estimated_remaining_sprays` | `Long` → `BIGINT` | YES | derived count |
| `predicted_quantity`, `sensor_quantity`, `discrepancy` | `BigDecimal` → `DECIMAL(12,3)` | NO | CHECK candidates non-negative |
| `status` | `ReconciliationStatus` → `VARCHAR(30)` | NO | STRING enum |
| `low_stock` | `Boolean` → `BOOLEAN/TINYINT(1)` | NO | app derived |
| `measured_at` | `LocalDateTime` → `DATETIME(6)` | NO | event time, request supplied |
| `processed_at` | `LocalDateTime` → `DATETIME(6)` | NO | `@PrePersist`, immutable |

### `InventoryAuditEvent` → `inventory_audit_events`

| Column | Java → SQL | Null | Constraint / behavior |
|---|---|---:|---|
| `tenant_id` | `Long` → `BIGINT` | NO | external tenant |
| `actor_id` | `Long` → `BIGINT` | YES | external Identity ref |
| `action` | `InventoryAuditAction` → `VARCHAR(60)` | NO | STRING enum |
| `entity_type` | `String` → `VARCHAR(50)` | NO | polymorphic logical type |
| `entity_id` | `Long` → `BIGINT` | YES | polymorphic logical ID; no FK |
| `machine_id`, `slot_id` | `Long` → `BIGINT` | YES | external Machine refs |
| `before_value`, `after_value` | `String` → `VARCHAR(1000)` | YES | snapshots |
| `reason` | `String` → `VARCHAR(500)` | YES | — |
| `reference_id` | `String` → `VARCHAR(150)` | YES | generic external/idempotency reference |
| `created_at` | `LocalDateTime` → `DATETIME(6)` | NO | `@PrePersist`, immutable |

## 8. Operation Inventory proposed physical schema map

```text
scentstation_operation
├── inventory_batches
│   ├── PK id; UK (tenant_id,batch_code)
│   ├── external: tenant_id, product_id (NO cross-service FK)
│   └── CHECK candidates: received_quantity>0, expiry_date IS NULL OR expiry_date>=received_at
├── bottles
│   ├── PK id; UK (tenant_id,bottle_code)
│   ├── internal FK candidate: (tenant_id,batch_id) -> inventory_batches tenant/id
│   ├── external: product_id; enum CHECK: status
│   └── quantity/calibration CHECK candidates
├── bottle_installations
│   ├── PK id; active slot UK; active bottle UK
│   ├── internal FK candidate: (tenant_id,bottle_id) -> bottles
│   ├── external: machine_id, slot_id, actor IDs
│   └── lifecycle CHECK: active/removed_at/removed_by consistency
├── dispense_consumptions
│   ├── PK id; UK (tenant_id,command_id)
│   ├── internal FK candidate: (tenant_id,bottle_id) -> bottles
│   └── external: machine_id, slot_id; enum CHECKs
├── bottle_movements
│   ├── PK id; internal FK candidate bottle; external machine/slot/actor
│   └── enum CHECK: movement_type
├── inventory_adjustments
│   ├── PK id; internal FK candidate bottle; external actor
│   └── non-negative quantity CHECKs
├── sensor_reconciliations
│   ├── PK id; UK (tenant_id,sensor_event_id)
│   ├── internal FK candidate bottle; external machine/slot
│   └── enum/status and non-negative quantity CHECKs
└── inventory_audit_events
    ├── PK id; polymorphic entity_id (no FK)
    ├── external tenant/actor/machine/slot
    └── enum CHECK: action
```

## 9. Operation Maintenance entity inventory

Mọi PK dùng `BIGINT AUTO_INCREMENT`. Các `ticket_id` là internal logical refs tới `maintenance_tickets.id`, nhưng source hiện không khai báo FK.

### `MaintenanceTicket` → `maintenance_tickets`

| Column | Java → SQL | Null | Constraint / behavior |
|---|---|---:|---|
| `tenant_id` | `Long` → `BIGINT` | NO | external tenant |
| `machine_id` | `Long` → `BIGINT` | NO | external Machine ref |
| `slot_id`, `location_id` | `Long` → `BIGINT` | YES | external Machine/location refs |
| `bottle_id` | `Long` → `BIGINT` | YES | internal Inventory logical ref; no FK |
| `title` | `String` → `VARCHAR(200)` | NO | — |
| `description` | `String` → `VARCHAR(2000)` | NO | — |
| `category` | `String` → `VARCHAR(100)` | NO | free text |
| `severity`, `priority`, `status` | enum → `VARCHAR(30)` | NO | STRING enums; app defaults |
| `reported_by_actor_id` | `Long` → `BIGINT` | NO | external Identity ref |
| `assigned_to_actor_id` | `Long` → `BIGINT` | YES | external Identity ref |
| `due_at` | `LocalDateTime` → `DATETIME(6)` | YES | app supplied |
| `created_at`, `updated_at` | `LocalDateTime` → `DATETIME(6)` | NO | app create/update callbacks |
| `closed_at` | `LocalDateTime` → `DATETIME(6)` | YES | service sets/clears on close/reopen |
| `critical` | primitive `boolean` → `BOOLEAN/TINYINT(1)` | NO | Java default false |

Indexes: `idx_maintenance_tenant_status(tenant_id,status)`, `idx_maintenance_tenant_machine(tenant_id,machine_id)`.

### `MaintenanceAssignmentHistory` → `maintenance_assignment_history`

| Column | Java → SQL | Null | Constraint / behavior |
|---|---|---:|---|
| `tenant_id`, `ticket_id` | `Long` → `BIGINT` | NO | tenant external; ticket internal logical ref |
| `previous_technician_actor_id` | `Long` → `BIGINT` | YES | external Identity |
| `technician_actor_id`, `performed_by_actor_id` | `Long` → `BIGINT` | NO | external Identity |
| `event_type` | enum → `VARCHAR(30)` | NO | STRING enum |
| `created_at` | `LocalDateTime` → `DATETIME(6)` | NO | app generated, immutable |

Indexes: `(tenant_id,ticket_id)`, `(tenant_id,technician_actor_id)`.

### `MaintenanceChecklistItem` → `maintenance_checklist_items`

| Column | Java → SQL | Null | Constraint / behavior |
|---|---|---:|---|
| `tenant_id`, `ticket_id` | `Long` → `BIGINT` | NO | tenant external; ticket internal logical ref |
| `item_name` | `String` → `VARCHAR(200)` | NO | — |
| `description` | `String` → `VARCHAR(1000)` | YES | — |
| `required_item` | primitive `boolean` → `BOOLEAN/TINYINT(1)` | NO | property name `required`; explicit column avoids keyword |
| `completed` | primitive `boolean` → `BOOLEAN/TINYINT(1)` | NO | lifecycle flag |
| `result_note` | `String` → `VARCHAR(2000)` | YES | — |
| `completed_by_actor_id` | `Long` → `BIGINT` | YES | external Identity |
| `completed_at` | `LocalDateTime` → `DATETIME(6)` | YES | service lifecycle time |
| `created_at` | `LocalDateTime` → `DATETIME(6)` | NO | app generated, immutable |

Index: `idx_maintenance_checklist_ticket(tenant_id,ticket_id)`.

### `MaintenanceRepairLog` → `maintenance_repair_logs`

| Column | Java → SQL | Null | Constraint / behavior |
|---|---|---:|---|
| `tenant_id`, `ticket_id` | `Long` → `BIGINT` | NO | tenant external; ticket internal logical ref |
| `diagnosed_by_actor_id` | `Long` → `BIGINT` | NO | external Identity |
| `diagnostic_result`, `corrective_action` | `String` → `VARCHAR(2000)` | NO | — |
| `replaced_part` | `String` → `VARCHAR(500)` | YES | — |
| `cost` | `BigDecimal` → `DECIMAL(15,2)` | YES | CHECK candidate `>= 0` |
| `repair_result` | enum → `VARCHAR(30)` | NO | STRING enum |
| `note` | `String` → `VARCHAR(2000)` | YES | — |
| `evidence_url` | `String` → `VARCHAR(1000)` | YES | — |
| `created_at` | `LocalDateTime` → `DATETIME(6)` | NO | app generated, immutable |

Index: `idx_repair_log_ticket(tenant_id,ticket_id)`.

### `PostMaintenanceTest` → `post_maintenance_tests`

| Column | Java → SQL | Null | Constraint / behavior |
|---|---|---:|---|
| `tenant_id`, `ticket_id` | `Long` → `BIGINT` | NO | tenant external; ticket internal logical ref |
| `machine_id` | `Long` → `BIGINT` | NO | external Machine |
| `requested_by_actor_id` | `Long` → `BIGINT` | NO | external Identity |
| `status` | enum → `VARCHAR(20)` | NO | STRING enum |
| `external_test_id` | `String` → `VARCHAR(150)` | YES | external Machine id; UK with tenant |
| `result_message` | `String` → `VARCHAR(2000)` | YES | — |
| `requested_at` | `LocalDateTime` → `DATETIME(6)` | NO | app generated, immutable |
| `completed_at` | `LocalDateTime` → `DATETIME(6)` | YES | service sets on final result |

Constraint/index: `uk_post_maintenance_test_external(tenant_id,external_test_id)` and `idx_post_maintenance_test_ticket(tenant_id,ticket_id)`. Multiple NULL external IDs are intentionally possible before adapter response.

### `MaintenanceAlertLink` → `maintenance_alert_links`

| Column | Java → SQL | Null | Constraint / behavior |
|---|---|---:|---|
| `tenant_id` | `Long` → `BIGINT` | NO | external tenant; UK part |
| `source_alert_id` | `String` → `VARCHAR(150)` | NO | external source id; UK part |
| `alert_type` | `String` → `VARCHAR(100)` | NO | free text |
| `ticket_id` | `Long` → `BIGINT` | NO | internal logical ticket ref |
| `machine_id` | `Long` → `BIGINT` | NO | external Machine |
| `occurred_at` | `LocalDateTime` → `DATETIME(6)` | NO | upstream event time |
| `created_at` | `LocalDateTime` → `DATETIME(6)` | NO | app generated, immutable |

Constraint/index: `uk_maintenance_alert_tenant_alert(tenant_id,source_alert_id)` and `idx_maintenance_alert_ticket(tenant_id,ticket_id)`.

### `MaintenanceAuditEvent` → `maintenance_audit_events`

| Column | Java → SQL | Null | Constraint / behavior |
|---|---|---:|---|
| `tenant_id` | `Long` → `BIGINT` | NO | external tenant |
| `action` | enum → `VARCHAR(60)` | NO | STRING enum |
| `entity_type` | `String` → `VARCHAR(50)` | NO | polymorphic type |
| `entity_id`, `ticket_id` | `Long` → `BIGINT` | YES | polymorphic/internal logical IDs; no FK |
| `actor_id` | `Long` → `BIGINT` | YES | external Identity |
| `machine_id` | `Long` → `BIGINT` | YES | external Machine |
| `before_state`, `after_state` | `String` → `VARCHAR(2000)` | YES | snapshots |
| `source` | `String` → `VARCHAR(50)` | NO | producer marker |
| `created_at` | `LocalDateTime` → `DATETIME(6)` | NO | app generated, immutable |

No declared index or unique constraint.

## 10. Operation Maintenance proposed physical schema map

```text
scentstation_operation
├── maintenance_tickets
│   ├── PK id; indexes (tenant_id,status), (tenant_id,machine_id)
│   ├── internal FK candidate (tenant_id,bottle_id) -> bottles
│   ├── external machine/slot/location/actor refs (NO cross-service FK)
│   └── enum + lifecycle CHECK candidates
├── maintenance_assignment_history
│   ├── PK id; internal FK candidate ticket; external actor refs
│   └── indexes tenant/ticket and tenant/technician
├── maintenance_checklist_items
│   ├── PK id; internal FK candidate ticket
│   └── index tenant/ticket; completion lifecycle CHECK
├── maintenance_repair_logs
│   ├── PK id; internal FK candidate ticket
│   └── index tenant/ticket; repair enum and cost CHECK
├── post_maintenance_tests
│   ├── PK id; UK (tenant_id,external_test_id); index tenant/ticket
│   ├── internal FK candidate ticket; external machine/actor
│   └── status/completed lifecycle CHECK
├── maintenance_alert_links
│   ├── PK id; UK (tenant_id,source_alert_id); index tenant/ticket
│   ├── internal FK candidate ticket
│   └── external alert/machine refs
└── maintenance_audit_events
    ├── PK id; ticket/entity logical refs
    └── external tenant/actor/machine; action CHECK
```

## 11. Internal relationship map

No database FK exists today. Recommended internal FK candidates for V1 must include tenant in validation/design so a child cannot point to another tenant’s row.

```text
CATALOG
products: no internal relationship

INVENTORY
inventory_batches 1 ── * bottles                    via bottles.batch_id
bottles           1 ── * bottle_installations       via bottle_id
bottles           1 ── * dispense_consumptions      via bottle_id
bottles           1 ── * bottle_movements           via bottle_id
bottles           1 ── * inventory_adjustments      via bottle_id
bottles           1 ── * sensor_reconciliations     via bottle_id
inventory_audit_events.entity_id: polymorphic; do not add ordinary FK

MAINTENANCE
maintenance_tickets 1 ── * maintenance_assignment_history
maintenance_tickets 1 ── * maintenance_checklist_items
maintenance_tickets 1 ── * maintenance_repair_logs
maintenance_tickets 1 ── * post_maintenance_tests
maintenance_tickets 1 ── * maintenance_alert_links
maintenance_tickets 1 ── * maintenance_audit_events (ticket_id nullable)
bottles             1 ── * maintenance_tickets (optional bottle_id; same operation DB)
maintenance_audit_events.entity_id: polymorphic; no ordinary FK
```

## 12. Cross-service scalar references

| Reference | Owner/dependency | Current use | Integration classification | DB rule |
|---|---|---|---|---|
| `tenantId` | Identity/platform | all domains | database scalar only | NO FK |
| actor IDs (`actorId`, installed/removed/reported/assigned/technician/etc.) | Identity | audit and lifecycle attribution | database scalar only | NO FK |
| `productId` | Catalog | batch, bottle, slot compatibility | scalar; `MachineSlotClient` contract also carries it | NO FK from Operation to Catalog |
| `machineId`, `slotId`, `locationId` | Machine | inventory placement, dispense, reconciliation, maintenance | scalar + interface; current implementation `LocalMachineSlotClient` is local/test placeholder, not HTTP | NO FK |
| `externalTestId` | Machine | post-maintenance request/result correlation | `PostMaintenanceMachineClient`; current local/test adapter returns UUID string, no real HTTP implementation | NO FK |
| `sourceAlertId` | upstream alert producer (not proven from another service source) | critical alert idempotency | request/database scalar | NO FK |
| `commandId` | upstream dispense command | idempotency | request/database scalar | NO FK |

Không có direct database access hoặc cross-service database FK trong hai service được audit. Catalog hiện không có HTTP integration.

## 13. Datatype consistency audit

| Identifier | Current Java type | Locations | Consistent? | Risk | Proposed SQL |
|---|---|---|---:|---|---|
| `tenantId` | `Long` | Product; tất cả Inventory/Maintenance entities, DTOs, repos | YES | Low | `BIGINT` |
| `actorId` and named actor IDs | `Long` | audit, adjustment, movement, installation, ticket, assignment, checklist, repair, post-test | YES | Low | `BIGINT` |
| `productId` | `Long` | InventoryBatch, Bottle, machine-slot DTO | YES | Low | `BIGINT` |
| `machineId` | `Long` | Inventory placement/events; Maintenance ticket/test/alert/audit | YES | Low | `BIGINT` |
| `locationId` | `Long` | MaintenanceTicket and request/response | YES | Low | `BIGINT` |
| `slotId` | `Long` | Inventory + MaintenanceTicket/audit/request DTOs | YES | Low | `BIGINT` |
| `ticketId` | `Long` | all Maintenance child entities/services/repos | YES | Low | `BIGINT` |
| `bottleId` | `Long` | Inventory entities and MaintenanceTicket | YES | Low | `BIGINT` |
| `batchId` | `Long` | Bottle and inventory API/service | YES | Low | `BIGINT` |
| `commandId` | `String` | Dispense request/entity/response/repo | YES | Low; max length must be contractually <=150 | `VARCHAR(150)` |
| `externalTestId` | `String` | PostMaintenanceTest + DTO/repo/client result | YES | Low; max length <=150 | `VARCHAR(150)` |
| `sourceAlertId` | `String` | MaintenanceAlertLink + request/history/repo | YES | Low; max length <=150 | `VARCHAR(150)` |

Không phát hiện cùng một loại ID dùng lẫn `Long`/`String` trong Operation; không có HIGH RISK datatype conflict nội bộ.

## 14. Enum persistence audit

Tất cả enum persisted dùng `@Enumerated(EnumType.STRING)`, an toàn hơn ordinal nhưng DB hiện chấp nhận chuỗi ngoài tập giá trị nếu không có CHECK.

| Enum | Table.column | Values | Assessment |
|---|---|---|---|
| `BottleStatus` | `bottles.status` | AVAILABLE, INSTALLED, NEARLY_EMPTY, EMPTY, DAMAGED, EXPIRED | NEEDS DATABASE CHECK CONSTRAINT |
| `BottleMovementType` | `bottle_movements.movement_type` | INSTALL, REMOVE, REPLACE, REFILL, CUSTOMER_CONSUMPTION, DIAGNOSTIC_CONSUMPTION, DISPOSE, ADJUSTMENT | NEEDS DATABASE CHECK CONSTRAINT |
| `DispenseType` | `dispense_consumptions.dispense_type` | CUSTOMER, DIAGNOSTIC | NEEDS DATABASE CHECK CONSTRAINT |
| `DispenseResult` | `dispense_consumptions.result` | SUCCESS, FAILED, TIMEOUT, UNCERTAIN | NEEDS DATABASE CHECK CONSTRAINT |
| `ReconciliationStatus` | `sensor_reconciliations.status` | NORMAL, DISCREPANCY, POSSIBLE_LEAK | NEEDS DATABASE CHECK CONSTRAINT |
| `InventoryAuditAction` | `inventory_audit_events.action` | BATCH_CREATED, BOTTLE_CREATED, BOTTLE_INSTALLED, BOTTLE_REMOVED, BOTTLE_REPLACED, BOTTLE_REFILLED, DISPENSE_RECORDED, SENSOR_RECONCILIATION, INVENTORY_ADJUSTMENT, SPRAY_CALIBRATION_UPDATED | RISKY/needs CHECK; expanding audit vocabulary requires migration |
| `MaintenanceSeverity` | `maintenance_tickets.severity` | MINOR, MODERATE, MAJOR, CRITICAL | NEEDS DATABASE CHECK CONSTRAINT |
| `MaintenancePriority` | `maintenance_tickets.priority` | LOW, MEDIUM, HIGH, URGENT | NEEDS DATABASE CHECK CONSTRAINT |
| `MaintenanceStatus` | `maintenance_tickets.status` | OPEN, ASSIGNED, IN_PROGRESS, WAITING_FOR_PARTS, READY_FOR_TEST, COMPLETED, CANCELLED | NEEDS DATABASE CHECK CONSTRAINT |
| `MaintenanceAssignmentEventType` | `maintenance_assignment_history.event_type` | ASSIGNED, REASSIGNED, ACKNOWLEDGED | NEEDS DATABASE CHECK CONSTRAINT |
| `MaintenanceRepairResult` | `maintenance_repair_logs.repair_result` | SUCCESS, PARTIAL, FAILED | NEEDS DATABASE CHECK CONSTRAINT |
| `PostMaintenanceTestStatus` | `post_maintenance_tests.status` | REQUESTED, RUNNING, PASSED, FAILED | NEEDS DATABASE CHECK CONSTRAINT |
| `MaintenanceAuditAction` | `maintenance_audit_events.action` | TICKET_CREATED, TICKET_UPDATED, TECHNICIAN_ASSIGNED, TECHNICIAN_REASSIGNED, ASSIGNMENT_ACKNOWLEDGED, CHECKLIST_CREATED, CHECKLIST_COMPLETED, REPAIR_RECORDED, CRITICAL_ALERT_TICKET_CREATED, POST_TEST_REQUESTED, POST_TEST_PASSED, POST_TEST_FAILED, TICKET_CLOSED, TICKET_REOPENED | RISKY/needs CHECK; expanding audit vocabulary requires migration |

Catalog không persist enum.

## 15. Money / quantity precision audit

| Field group | Mapping | Assessment |
|---|---|---|
| Product `defaultPrice` | `BigDecimal`, `DECIMAL(12,2)` | SAFE for ordinary price; add non-negative CHECK |
| Maintenance `cost` | `BigDecimal`, `DECIMAL(15,2)`, nullable | SAFE; add non-negative CHECK and define currency outside this column |
| Inventory quantities, ml/spray calibration, sensor/discrepancy | `BigDecimal`, `DECIMAL(12,3)` | SAFE from binary floating-point loss; confirm 0.001 unit resolution and maximum capacity |
| `receivedQuantity` | `Integer`, `INT` | Whole-bottle count is appropriate; add positive CHECK |
| estimated/successful sprays | `Long`, `BIGINT` | Whole-count appropriate |
| reconciliation tolerance | `BigDecimal` constants and explicit rounding | SAFE in Java |

Không phát hiện persisted monetary/quantity field dùng `double`/`float`. Rủi ro P1 là thiếu DB CHECK cho negative values và không nhất quán nullability của dispense quantity snapshots, không phải floating-point loss.

## 16. Timestamp audit

| Table / fields | Required? | Producer / update behavior | Audit finding |
|---|---|---|---|
| products `created_at`, `updated_at` | both required | app callbacks; update on JPA update | no DB default; bulk/native update could bypass |
| inventory_batches `created_at`, `updated_at` | required | app callbacks | same |
| bottles `created_at`, `updated_at` | required | app callbacks | same |
| bottle_installations `installed_at` | required | app default if absent | `removed_at` nullable and service-set; lifecycle CHECK absent |
| dispense `processed_at` | required | app `@PrePersist` | immutable mapping |
| movements/adjustments/inventory audit `created_at` | required | app `@PrePersist` | immutable mapping |
| reconciliation `measured_at` | required request event time | caller | `processed_at` required/app-generated; ordering semantics must distinguish them |
| maintenance ticket `created_at`,`updated_at` | required | app callbacks | `due_at`,`closed_at` nullable; closed lifecycle CHECK absent |
| assignment/checklist/repair/alert/audit `created_at` | required | app `@PrePersist` | checklist `completed_at` nullable; consistency CHECK absent |
| post-test `requested_at` | required | app `@PrePersist` | `completed_at` nullable/service-set; final-status consistency CHECK absent |
| alert `occurred_at` | required | upstream/request | distinct from app `created_at` |

`LocalDateTime` maps to timezone-less MySQL `DATETIME(6)` (or the exact Hibernate-generated equivalent). Source has no UTC/zone policy. This is P1 because app servers in different zones can create incomparable times. No timestamp is DB-generated.

## 17. Index audit

### Existing

- Unique constraints create supporting unique indexes: batch `(tenant_id,batch_code)`, bottle `(tenant_id,bottle_code)`, active installation slot/bottle keys, dispense `(tenant_id,command_id)`, reconciliation `(tenant_id,sensor_event_id)`, post-test `(tenant_id,external_test_id)`, alert `(tenant_id,source_alert_id)`.
- Maintenance: ticket `(tenant_id,status)`, `(tenant_id,machine_id)`; assignment `(tenant_id,ticket_id)`, `(tenant_id,technician_actor_id)`; checklist `(tenant_id,ticket_id)`; repair `(tenant_id,ticket_id)`; post-test `(tenant_id,ticket_id)`; alert `(tenant_id,ticket_id)`.

### Missing candidates

Candidates below derive directly from repository method predicates/order. Exact set/order must be confirmed with MySQL `EXPLAIN` and workload before DB-02/03.

| Table | Candidate | Query reason |
|---|---|---|
| `products` | `(tenant_id,id)` / at minimum `(tenant_id)` | tenant-scoped reads must replace current global access |
| `inventory_batches` | `(tenant_id,product_id)` | `findAllByTenantIdAndProductId`; UK covers tenant-only prefix for simple tenant list |
| `bottles` | `(tenant_id,batch_id)`, `(tenant_id,product_id)`, `(tenant_id,status)` | repository filters |
| `bottle_installations` | `(tenant_id,bottle_id,installed_at DESC)`, `(tenant_id,machine_id,installed_at DESC)` | history queries; active UKs help active lookup only |
| `dispense_consumptions` | `(tenant_id,bottle_id,processed_at DESC)`, `(tenant_id,machine_id,processed_at DESC)`, `(tenant_id,bottle_id,result)` | history/count queries |
| `bottle_movements` | `(tenant_id,created_at DESC)`, `(tenant_id,bottle_id,created_at DESC)`, `(tenant_id,machine_id,created_at DESC)`, `(tenant_id,movement_type,created_at DESC)` | four repository histories |
| `inventory_adjustments` | `(tenant_id,created_at DESC)`, `(tenant_id,bottle_id,created_at DESC)` | history queries |
| `sensor_reconciliations` | `(tenant_id,bottle_id,processed_at DESC)`, `(tenant_id,machine_id,processed_at DESC)` | history queries |
| `inventory_audit_events` | `(tenant_id,created_at DESC)`, `(tenant_id,action,created_at DESC)`, `(tenant_id,entity_type,entity_id,created_at DESC)` | audit queries |
| `maintenance_tickets` | `(tenant_id,machine_id,created_at DESC,id DESC)`, `(tenant_id,assigned_to_actor_id)` | ordered machine history; technician list |
| `maintenance_assignment_history` | `(tenant_id,ticket_id,created_at,id)`, `(tenant_id,technician_actor_id,created_at DESC)` | ordered histories |
| `maintenance_checklist_items` | `(tenant_id,ticket_id,required_item,completed)` | incomplete-required existence query; existing prefix may suffice for small sets |
| `maintenance_repair_logs` | `(tenant_id,ticket_id,created_at,id)` | ordered histories |
| `post_maintenance_tests` | `(tenant_id,ticket_id,requested_at,id)`, optionally `(tenant_id,ticket_id,status)` | ordered/latest and passed existence queries |
| `maintenance_audit_events` | `(tenant_id,ticket_id,created_at,id)` | only repository query currently has no index |

### Redundant candidates

- Không có declared index chắc chắn redundant tại source hiện tại.
- Không tạo standalone indexes trùng left-most prefix của unique/index hiện có, ví dụ `(tenant_id)` trên batch/bottle/dispense/reconciliation hoặc `(tenant_id,ticket_id)` bổ sung cho Maintenance tables đã có.
- Hai active-installation unique indexes đều cần cho hai business rules khác nhau; không redundant.

## 18. Unique constraint / idempotency audit

| Business rule | Java validation | DB unique constraint | Test coverage | Risk |
|---|---|---|---|---|
| Product business key per tenant | none | none | none | P1: key chưa được định nghĩa; duplicates possible |
| Batch code per tenant | pre-check exists | YES `(tenant,batch_code)` | unit duplicate test | protected; service should translate DB race consistently |
| Bottle code per tenant | pre-check exists | YES `(tenant,bottle_code)` | service/controller duplicate tests | protected |
| One active installation per slot | pre-check | YES nullable-active composite | H2 DB constraint integration | P0 before MySQL migration: verify MySQL semantics/index DDL and lifecycle NULL convention |
| One active installation per bottle | pre-check | YES nullable-active composite | service tests; no explicit second-bottle DB constraint test found | P1 test gap |
| Dispense command once per tenant | pre-check exists | YES `(tenant,command_id)` | integration verifies no double deduct/audit | protected transactionally; P1 duplicate DB exception translation under concurrency |
| Sensor event once per tenant | read existing and return it | YES `(tenant,sensor_event_id)` | duplicate service test | P1 race may surface raw integrity exception instead of returning winner |
| Critical alert once per tenant | pre-check + catch `DataIntegrityViolationException` | YES `(tenant,source_alert_id)` | repository/service/integration including concurrent winner | SAFE |
| External post-test id per tenant | lookup by key | YES `(tenant,external_test_id)`, nullable | service/integration persistence tests | protected after ID assigned; duplicate adapter ID fails transaction |
| Sample data marker `(tenant,title)` | exists check only | none | initializer idempotency test | P2 local-profile-only race; not production uniqueness |

DB unique constraints protect the principal race-sensitive rules except Product key and sample marker. Java-only pre-checks are not sufficient by themselves; the listed DB constraints are the actual guard.

## 19. Tenant isolation audit

### Catalog — RISK

- `ProductRepository` has no tenant-scoped method.
- `ProductService.getProducts()` calls global `findAll()`.
- get/update price/change availability call `findById(id)` only.
- Requests contain `tenantId` for create, but read/update APIs do not enforce it.
- Classification: **RISK / P0 security and ownership boundary**. DB-02 must make every business read/update tenant-scoped and index accordingly; DB-01 does not modify it.

### Inventory — SAFE with noted repository escape hatch

- Business services consistently use `findByIdAndTenantId`, tenant-qualified history/exists methods, and validate installation within tenant.
- Integration tests cover cross-tenant bottle read/adjust/install rejection.
- Inherited `findById/findAll/count` remain callable and are used by tests/support, but the audit found no direct unscoped use in production Inventory business logic.

### Maintenance — SAFE with noted repository escape hatch

- Business services use `findByIdAndTenantId` and tenant-qualified child queries.
- Repository/service/end-to-end tests cover cross-tenant ticket, checklist, repair, post-test, alert, and history behavior.
- Inherited unscoped methods remain technically available; keep them out of business paths or replace with guarded repository abstraction later.

## 20. Current MySQL/H2 configuration

| Item | Catalog | Operation |
|---|---|---|
| Runtime URL | `${CATALOG_DB_URL:jdbc:mysql://localhost:3306/scentstation_catalog}` | `${OPERATION_DB_URL:jdbc:mysql://localhost:3306/scentstation_operation}` |
| Credentials | service-specific env vars, empty defaults | service-specific env vars, empty defaults |
| Driver/dependency | MySQL Connector/J runtime | MySQL Connector/J runtime |
| H2 | test scope; MySQL mode | test scope; MySQL mode |
| Test DB | `catalog_test_db`, `DB_CLOSE_DELAY=-1`, create-drop, explicit H2 dialect | `operation_test_db`, `DB_CLOSE_DELAY=-1`, create-drop, test profile, dialect implicit |
| Runtime `ddl-auto` | not specified | not specified |
| Flyway/Liquibase | absent | absent |
| `open-in-view` | not specified (Spring Boot default applies; normally enabled for web apps) | same |
| Connection pool/tuning | not specified; Boot/Hikari defaults | same |

Với non-embedded MySQL, không nên dựa vào schema auto-creation mặc định. Một database trống không có migration sẽ không có tables; một schema có sẵn cũng không được version/validate có chủ đích. Đây là blocker trước production MySQL.

## 21. Current Docker assumptions

| Check | Catalog | Operation |
|---|---|---|
| Build/runtime Java | Maven 3.9.9 + Temurin 21; Temurin 21 JRE | same |
| Exposed port | 8082 | 8085 |
| Runtime jar | `catalog-service-*.jar` copied to `app.jar` | `operation-service-*.jar` copied to `app.jar` |
| DB env | Dockerfile itself declares none; Spring reads `CATALOG_DB_*` | reads `OPERATION_DB_*` |
| Default DB host | `localhost:3306` | `localhost:3306` |
| Compose/network | root compose is empty skeleton (`services: {}`) | same |

DK-01 must supply service-specific DB URLs/credentials and Docker DNS host (not container-local `localhost`), databases/users, network, dependency health/readiness, and secrets strategy. Docker images build, but current repository has no runnable container topology.

## 22. Catalog risks

### P0

1. **No versioned schema/migration and no explicit runtime validation policy:** a clean MySQL database cannot serve the entity reliably.
2. **Tenant isolation failure:** global `findAll()`/`findById()` in production business logic can expose or modify another tenant’s product.

### P1

1. No agreed tenant-scoped product business key/unique constraint; duplicate names/records are possible.
2. No indexes for future mandatory tenant-scoped reads.
3. No DB CHECK for non-negative price.
4. Timestamp/column naming/timezone behavior relies on implicit Hibernate/application conventions.
5. Default MySQL credentials are empty and `localhost` is not container-network safe.

### P2

1. `image_url` default length 255 may be too short for signed/CDN URLs.
2. `open-in-view` is implicit rather than explicitly documented.

## 23. Operation risks

### P0

1. **No versioned schema/migration and no explicit runtime validation policy:** clean MySQL has no 15 required tables.
2. **Active-installation unique strategy must be proven on target MySQL:** correctness depends on nullable `active` and MySQL NULL-in-unique semantics; H2 coverage alone is insufficient before V1 migration.
3. **No internal FKs at all:** bottles can orphan from batches and all Inventory/Maintenance child rows can orphan/cross-link tenants unless V1 creates tenant-safe internal constraints.

### P1

1. Most Inventory repository query patterns lack supporting composite indexes; Maintenance audit query also lacks an index.
2. Sensor idempotency and several pre-check+insert flows may return raw integrity errors under concurrency even though unique constraints preserve data.
3. Missing quantity/money/lifecycle/enum CHECK constraints permits invalid direct writes.
4. `LocalDateTime` has no documented UTC/timezone policy; all DB timestamps are app-generated.
5. Active installation DB test covers duplicate slot but not explicit duplicate active bottle, repeated remove/history, or target MySQL.
6. Internal tenant-safe FK design may require supporting `(tenant_id,id)` uniqueness/index on parent tables.
7. Default MySQL credentials are empty; Docker default URL uses `localhost`; compose is empty.

### P2

1. Generic polymorphic audit IDs cannot be FK-enforced and need retention/size policy.
2. Several long strings are `VARCHAR(1000/2000)`; future payload growth may warrant `TEXT`, based on measured data.
3. Sample initializer idempotency by `(tenant,title)` is Java-only, but initializer is local-profile scoped.

## 24. Recommended Catalog V1 schema contents

- Tables: `products` only.
- PK: `products.id BIGINT AUTO_INCREMENT`.
- Indexes: tenant-scoped lookup/list index; exact order tied to corrected repository query.
- Constraints: required/nullability/length/precision from entity; `default_price >= 0`; product business key unique only after business contract decides the key.
- External refs: `tenant_id` scalar only, no Identity DB FK.
- Migration should explicitly name every column/index/constraint and define `DATETIME(6)` policy.

## 25. Recommended Operation V1 schema contents

- Tables: all 15 audited tables (8 Inventory + 7 Maintenance).
- Preserve declared unique rules for batch, bottle, active slot/bottle, command, sensor event, external test, and source alert.
- Add query-driven indexes listed in section 17, after de-duplication against unique/index prefixes.
- Add tenant-safe internal FKs for batch→bottle, bottle→Inventory histories/installations/consumption/reconciliation, ticket→Maintenance children, and optional bottle→ticket.
- Do not add FK for tenant, actor, product, machine, slot, location, command, external test, or source alert IDs.
- Add enum CHECKs and non-negative quantity/cost/price checks.
- Add lifecycle checks where portable and supportable: active installation removal fields, completed checklist metadata, closed ticket timestamp, final test completion timestamp.
- Explicitly document MySQL engine/charset/collation and UTC/`DATETIME(6)` strategy.

## 26. Flyway readiness

- **Catalog: NOT READY.** P0 tenant access shape and initial physical schema decisions must be resolved before DB-02 writes V1.
- **Operation: READY WITH FIXES.** Entity set and principal unique rules are clear, but DB-03 must first settle target-MySQL active uniqueness and tenant-safe internal FK/index design.

This rating describes readiness to introduce a correct Flyway foundation, not current presence of Flyway (currently absent in both).

## 27. Docker readiness

- **Catalog: NOT READY.** Image definition exists, but no compose service/database, credentials, network-safe DB URL, health/readiness, or schema migration path.
- **Operation: NOT READY.** Same infrastructure gaps; additionally no production implementation for Machine integration is present (only local/test adapters).

## 28. Recommended next tasks

1. **DB-02 Catalog Persistence Foundation** — resolve Catalog P0s, then add versioned Catalog schema and explicit runtime validation in its own authorized task.
2. **DB-03 Operation Persistence Foundation** — after DB-02, implement the reviewed 15-table Operation foundation, target-MySQL constraint tests, indexes, and internal FKs.

Không đề xuất sửa source/schema của service do member khác sở hữu.

## 29. Files changed

- `docs/db-01-catalog-operation-database-audit.md`
- No production files.
- No test files.
- No config, POM, Dockerfile, SQL, or migration files.

## 30. Final recommendation

**CATALOG**

- Flyway readiness = **NOT READY**
- Docker readiness = **NOT READY**
- P0 issues = **2**

**OPERATION**

- Flyway readiness = **READY WITH FIXES**
- Docker readiness = **NOT READY**
- P0 issues = **3**

**Aggregate risk count:** P0 = **5**, P1 = **10**, P2 = **5**.

**DB-02 can start = YES**, với điều kiện DB-02 xử lý Catalog P0 trong đúng scope của task đó và không dùng report này như migration SQL.
