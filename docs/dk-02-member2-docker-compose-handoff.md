# DK-02 Member 2 Docker Compose Handoff

## 1. Objective

Dựng Compose cô lập cho `catalog-service`, `operation-service` và một MySQL dùng hai logical database với quyền tách biệt.

## 2. Git / Branch

- Branch: `feature/maintenance`.
- HEAD lúc bắt đầu: `3ddd61880d6262703350f9de30fead979dfd1b0c`.
- Không commit, push, merge hoặc rebase.

## 3. Scope

Chỉ thêm Compose Member 2, MySQL initialization script, ba biến password vào `.env.example` và handoff này. Không triển khai DK-03 hay infrastructure khác.

## 4. Files inspected

- Dockerfile, runtime configuration, Flyway migrations và profile configuration của Catalog/Operation.
- Root `.env.example`, `.gitignore`, `docker-compose.yml` ở chế độ chỉ đọc ngoại trừ phần bổ sung được phép trong `.env.example`.
- DK-01 handoff và trạng thái Git hiện hữu.

## 5. Files changed

- `compose.member2.yml`
- `.env.example`
- `docs/dk-02-member2-docker-compose-handoff.md`

Không cần sửa service code hoặc Dockerfile trong DK-02.

## 6. Compose topology

```text
mysql:8.0.41
├── scentstation_catalog  ← catalog_app
└── scentstation_operation ← operation_app

catalog-service   → mysql:3306/scentstation_catalog
operation-service → mysql:3306/scentstation_operation
```

Compose chỉ có ba service: `mysql`, `catalog-service`, `operation-service`.

## 7. MySQL version

`mysql:8.0.41`, cùng version đã được DB-02/DB-03 chứng minh. Không dùng tag `latest`.

## 8. Database ownership

- Catalog DB: `scentstation_catalog`; user: `catalog_app`; quyền chỉ trên `scentstation_catalog.*`.
- Operation DB: `scentstation_operation`; user: `operation_app`; quyền chỉ trên `scentstation_operation.*`.
- Application không dùng root credential.

## 9. MySQL initialization strategy

Compose config `mysql-init` được mount vào `docker-entrypoint-initdb.d` khi volume trống. Script nội tuyến chỉ tạo database, application users và scoped grants. Flyway chịu trách nhiệm toàn bộ business tables. Cách này tránh sửa `.gitignore`, nơi `infra/member2/**` hiện bị ignore.

## 10. Compose network

Dedicated bridge network key: `scentstation-member2`. Compose project prefix giữ các instance tách biệt; service dùng Docker DNS hostname `mysql`, không dùng `localhost`.

## 11. Volumes

Named volume key: `member2-mysql-data`. Dữ liệu sống qua `stop/start` và `down`; chỉ bị xóa bởi `down -v`. MySQL không publish host port, tránh xung đột 3306 và chỉ được truy cập nội bộ/qua `docker compose exec`.

## 12. Environment contract

Required local values trong `.env`:

- `MYSQL_ROOT_PASSWORD`
- `CATALOG_DB_PASSWORD`
- `OPERATION_DB_PASSWORD`

Catalog nhận URL `jdbc:mysql://mysql:3306/scentstation_catalog`, user `catalog_app`, port `8082`. Operation nhận URL `jdbc:mysql://mysql:3306/scentstation_operation`, user `operation_app`, port `8085`. Compose required-variable checks chặn startup nếu password trống.

## 13. Operation docker-local profile

Compose explicitly inject `SPRING_PROFILES_ACTIVE=docker-local` vì Machine Service ngoài isolated Member 2 scope. Profile `local` không được bật và Maintenance sample initializer không chạy. Full-team/integration Compose sau này không được dùng `docker-local`.

## 14. Healthchecks

- MySQL: container healthcheck dùng `mysqladmin ping`, có interval 5s, timeout 5s, 20 retries, start period 20s.
- Catalog: external smoke test gọi `GET http://localhost:8082/actuator/health` và nhận `UP`.
- Operation: external smoke test gọi `GET http://localhost:8085/actuator/health` và nhận `UP`.

Không cài curl/wget vào JRE image chỉ để healthcheck.

## 15. Startup dependency

Catalog và Operation dùng `depends_on.mysql.condition: service_healthy`. Acceptance log xác nhận MySQL healthy trước khi hai application container start.

## 16. Flyway bootstrap result

- Catalog: Flyway V1 applied; exact tables là `flyway_schema_history`, `products`.
- Operation: Flyway V1 applied; `flyway_schema_history` cộng 15 business tables: `inventory_batches`, `bottles`, `bottle_installations`, `dispense_consumptions`, `bottle_movements`, `inventory_adjustments`, `sensor_reconciliations`, `inventory_audit_events`, `maintenance_tickets`, `maintenance_assignment_history`, `maintenance_checklist_items`, `maintenance_repair_logs`, `post_maintenance_tests`, `maintenance_alert_links`, `maintenance_audit_events`.
- Logs xác nhận Hibernate EntityManager initialized sau migration.

## 17. Database privilege isolation result

- `catalog_app` → catalog DB: **PASS**.
- `catalog_app` → operation DB: **DENIED**.
- `operation_app` → operation DB: **PASS**.
- `operation_app` → catalog DB: **DENIED**.

## 18. Clean startup test

Command: `docker compose -p scentstation-member2 -f compose.member2.yml down -v`, sau đó `docker compose -p scentstation-member2 -f compose.member2.yml up -d --build`.

Result: **PASS**. Empty volume được tạo, init script hoàn tất, MySQL healthy, Flyway tạo schema và hai service lên `UP`. Acceptance lần đầu phát hiện bind-mounted executable script trên Windows không có entrypoint helper trong subprocess; final implementation chuyển sang Compose config nội tuyến và dùng MySQL client qua Unix socket. Clean startup đã được chạy lại thành công với artifact cuối.

## 19. Catalog health

`GET /actuator/health` → `{"status":"UP"}`.

## 20. Operation health

`GET /actuator/health` → `{"status":"UP"}`; log xác nhận active profile là `docker-local`.

## 21. Sample-data verification

`maintenance_tickets` initial count sau clean startup: **0**. Final clean reset cũng xác nhận count **0**.

## 22. Persistence restart test

Chèn một Catalog product marker bằng `catalog_app` và một Operation maintenance marker bằng `operation_app`; restart MySQL, chờ healthy, restart hai application container. Kết quả: hai health endpoint trở lại `UP`, mỗi marker còn đúng 1 row. Sau test, volume được reset và bootstrap lại để trả stack về clean state.

## 23. Catalog regression

- Tests: 13
- Failures: 0
- Errors: 0
- Skipped: 0
- BUILD: **SUCCESS**

## 24. Operation regression

- Tests: 265
- Failures: 0
- Errors: 0
- Skipped: 0
- BUILD: **SUCCESS**

## 25. docker compose config result

`docker compose -p scentstation-member2 -f compose.member2.yml config` với ba required password variables được set: **SUCCESS**. Không có unresolved variable.

## 26. Commands for developers

Tạo `.env` từ `.env.example`, điền ba password Member 2, rồi chạy:

```powershell
docker compose -p scentstation-member2 -f compose.member2.yml up --build
docker compose -p scentstation-member2 -f compose.member2.yml up -d --build
docker compose -p scentstation-member2 -f compose.member2.yml logs -f
docker compose -p scentstation-member2 -f compose.member2.yml ps
docker compose -p scentstation-member2 -f compose.member2.yml down
docker compose -p scentstation-member2 -f compose.member2.yml down -v
```

Theo thứ tự: start foreground, background, logs, status, stop, reset.

## 27. Known gaps

- DK-03/future integration phải thay `docker-local` bằng real Machine integration wiring.
- Full-team orchestration có thể chuẩn hóa application container healthchecks, deployment secrets và resource limits. DK-02 không thêm curl, restart policy hoặc premature CPU/memory limits; crash loop vẫn lộ rõ trong development.

## 28. Root Compose modified?

**NO**. `docker-compose.yml` không bị sửa.

## 29. Other services modified?

**NO**. Không sửa Identity, Gateway, Order, Machine, Reporting, frontend hoặc device.

## 30. DK-03 readiness

Can DK-03 begin: **YES**. DK-02 acceptance đã pass; không bắt đầu DK-03 trong task này.

## 31. Final recommendation

DK-02 = **DONE**.
