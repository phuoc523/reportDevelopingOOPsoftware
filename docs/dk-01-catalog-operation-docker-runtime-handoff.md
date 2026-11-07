# DK-01 — Catalog & Operation Docker Runtime Handoff

## 1. Objective

Chuẩn hóa Docker runtime cho `catalog-service` và `operation-service` trên Java 21, kết nối MySQL bằng biến môi trường, chạy Flyway trước Hibernate validation, chạy non-root và cung cấp health endpoint cho DK-02.

## 2. Baseline

- Branch lúc bắt đầu: `feature/maintenance`.
- HEAD lúc bắt đầu: `3ddd61880d6262703350f9de30fead979dfd1b0c`.
- Không commit, push, merge hoặc rebase.

## 3. Scope

Chỉ thay đổi Docker/runtime configuration của Catalog và Operation, hai annotation profile Java tối thiểu để hỗ trợ isolated Docker development khi được yêu cầu rõ ràng, và tài liệu handoff này. Không sửa root `docker-compose.yml` và không sửa service khác.

## 4. Files inspected

- Dockerfile, `pom.xml`, `application.yml`, migrations và application entry point của hai service.
- Root `.env.example`, `docker-compose.yml`, `README.md`, `CONTRIBUTING.md` ở chế độ chỉ đọc.
- Hai local adapter và các consumer của Operation để xác định lỗi thiếu bean khi chạy container.

## 5. Files changed for DK-01

- `services/catalog-service/Dockerfile`
- `services/catalog-service/.dockerignore`
- `services/catalog-service/pom.xml`
- `services/catalog-service/src/main/resources/application.yml`
- `services/operation-service/Dockerfile`
- `services/operation-service/.dockerignore`
- `services/operation-service/pom.xml`
- `services/operation-service/src/main/resources/application.yml`
- `services/operation-service/src/main/java/com/scentstation/operation/inventory/integration/machine/client/LocalMachineSlotClient.java`
- `services/operation-service/src/main/java/com/scentstation/operation/maintenance/integration/LocalPostMaintenanceMachineClient.java`
- `docs/dk-01-catalog-operation-docker-runtime-handoff.md`

## 6. Catalog before/after

Trước DK-01, image dùng runtime tag rộng `21-jre`, copy JAR bằng wildcard và chạy root. Sau DK-01, build tạo JAR có tên xác định, runtime dùng `eclipse-temurin:21.0.9_10-jre-jammy`, copy duy nhất `/app/app.jar` và chạy bằng `appuser`.

## 7. Operation before/after

Operation nhận cùng chuẩn multi-stage/non-root như Catalog. Image không đặt Spring profile mặc định và vì vậy không tự bật Machine simulator. Isolated Docker development phải chủ động chọn profile `docker-local`; sample-data initializer vẫn chỉ thuộc profile `local`.

## 8. Build strategy

- Build stage: `maven:3.9.9-eclipse-temurin-21`.
- Command: `mvn -q -DskipTests package`.
- Runtime stage chỉ chứa Temurin JRE và application JAR.
- Không hard-code heap flags; orchestration có thể cấp `JAVA_TOOL_OPTIONS` khi cần.

## 9. Runtime user

Cả hai image tạo system user/group `appuser`, copy JAR với ownership tương ứng và khai báo `USER appuser`. Kiểm tra image trả về `uid=999`, `gid=999`.

## 10. Ports

- Catalog: container port `8082`.
- Operation: container port `8085`.

## 11. Environment contract

- Catalog: `CATALOG_DB_URL`, `CATALOG_DB_USERNAME`, `CATALOG_DB_PASSWORD`, tùy chọn `CATALOG_SERVICE_PORT`.
- Operation: `OPERATION_DB_URL`, `OPERATION_DB_USERNAME`, `OPERATION_DB_PASSWORD`, tùy chọn `OPERATION_SERVICE_PORT`.
- Operation image environment-neutral, không đặt `SPRING_PROFILES_DEFAULT` hoặc tự chọn Machine adapter.
- DK-02 Member 2 isolated Compose phải explicitly inject `SPRING_PROFILES_ACTIVE=docker-local` vì Machine Service chưa nằm trong scope đó.
- Integration/full-team Docker sau này không được bật `docker-local`; orchestration phải cung cấp integration bean/profile thích hợp.

## 12. Startup lifecycle

Container khởi chạy bằng exec-form `java -jar app.jar`; Spring kết nối MySQL, Flyway validate/apply migration, sau đó Hibernate chạy `ddl-auto=validate` và web server nhận traffic.

## 13. Secrets

Không bake credential vào image. Username/password/URL phải được DK-02 inject qua environment hoặc secret mechanism. `.env` và `.env.*` bị loại khỏi Docker build context.

## 14. Docker context

Mỗi service có `.dockerignore` loại `target`, Git/IDE metadata, log và env files. Dockerfile chỉ copy `pom.xml` và `src` vào build stage.

## 15. Actuator and healthcheck

Actuator được thêm cho cả hai service. Chỉ expose `/actuator/health`, với `show-details: never`. Dockerfile không cài thêm `curl` và không khai báo `HEALTHCHECK`; DK-02 nên cấu hình Compose healthcheck phù hợp với image JRE tối giản.

## 16. Catalog image build

Command: `docker build -t scentstation/catalog-service:local .` trong `services/catalog-service`. Kết quả: thành công; runtime Java `Temurin 21.0.9+10 LTS`, non-root, port `8082`.

## 17. Operation image build

Command: `docker build -t scentstation/operation-service:local .` trong `services/operation-service`. Kết quả: thành công; runtime Java `Temurin 21.0.9+10 LTS`, non-root, port `8085`.

## 18. Catalog regression

`mvn clean test`: BUILD SUCCESS — 13 tests, 0 failures, 0 errors, 0 skipped.

## 19. Operation regression

`mvn clean test`: BUILD SUCCESS — 265 tests, 0 failures, 0 errors, 0 skipped.

## 20. Runtime smoke test

Hai image được chạy thực tế trên một Docker network tạm với hai MySQL `8.0.41` tạm. Catalog health `UP` và schema có 2 bảng. Operation được smoke test bằng explicit `SPRING_PROFILES_ACTIVE=docker-local`, health `UP`, schema có 16 bảng và `maintenance_tickets` có 0 sample rows. Generic Operation Docker runtime không tự fallback sang simulator. Tất cả container/network tạm đã được xóa sau kiểm thử.

## 21. Production Java change

Có, nhưng chỉ là mở rộng `@Profile` của `LocalMachineSlotClient` và `LocalPostMaintenanceMachineClient` từ `local/test` sang explicit development profile `local/test/docker-local`. Không sửa method hoặc business logic. Profile `docker-local` không kích hoạt `MaintenanceSampleDataInitializer`, vì initializer vẫn chỉ dùng profile `local`.

## 22. Other services

Không sửa service khác và không sửa root Compose. Các thay đổi tồn tại sẵn ngoài phạm vi DK-01 được giữ nguyên.

## 23. DK-02 readiness and remaining work

Sẵn sàng cho DK-02. Member 2 isolated Compose phải inject `SPRING_PROFILES_ACTIVE=docker-local`; future integration/full-team Compose không được dùng profile này. DK-02 còn phải khai báo hai MySQL service, network, volumes, credentials/env injection, service healthchecks và startup dependency/order trong root Compose. Không có phần Compose nào được triển khai trong DK-01.

## 24. Final status

**DONE** — cả hai image build thành công, chạy non-root trên Java 21, migrate/validate MySQL thành công, health endpoint trả `UP`, và toàn bộ regression test xanh.
