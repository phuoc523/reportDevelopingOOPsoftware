# JPA Alignment Report — Order Service

## A. Initial audit

### Kiến trúc trước refactor
- Order Service đang ở trạng thái chuẩn hóa Spring Data JPA theo nhánh `test-order-payment`.
- Mục tiêu là thay thế repository JDBC thủ công bằng JPA mapping, đồng thời giữ nguyên API contract và nghiệp vụ thanh toán/dispensing.
- Dữ liệu thuộc `order-service` vẫn được tách khỏi các service khác và không dùng database chung của member khác.

### Khác biệt với Catalog
- Catalog đang dùng Spring Boot 3.4.5 và JPA conventions rõ ràng, với `@Entity`, `@Table`, `@GeneratedValue`, `@Column` và repositories dựa trên `JpaRepository`.
- Order Service đã chuyển sang cùng cách tiếp cận để đồng bộ với quy chuẩn chung, nhưng cần giữ tenant ID dạng String vì Identity/Gateway hiện đang dùng UUID String, không phải Long.
- Order Service vẫn giữ schema MySQL native, không ép đổi kiểu dữ liệu tiền tệ hoặc khóa chính theo kiểu Catalog.

### Danh sách vấn đề chính
- Repository JDBC cũ có thể dễ gây lỗi khi xử lý payment callback, dispensing duplication và order status transitions.
- JPA cần đảm bảo `saveIfAbsent` không tạo trùng record cho payment và dispensing command.
- `payment_callback_events` và `transaction_reviews` phải bảo toàn idempotency và không ghi lại callback trùng không cần thiết.
- `tenant_id` cần được giữ tương thích với Identity/Gateway, không tự đổi sang Long để bắt chước Catalog.

## B. Changes implemented

| File | Thay đổi | Lý do | Ảnh hưởng |
|---|---|---|---|
| `services/order-service/pom.xml` | Duy trì Spring Data JPA và H2 test scope | Đảm bảo persistence JPA và test có DB phù hợp | Không phá vỡ build |
| `services/order-service/src/main/resources/application.yml` | Cấu hình datasource + `ddl-auto=validate` + schema script classpath | Khớp schema MySQL thật và validation mapping | Chạy đúng với H2 test và MySQL production |
| `services/order-service/database/schema.sql` | Giữ nguyên bảng `orders`, `order_items`, `payments`, `payment_callback_events`, `dispensing_commands`, `transaction_reviews` | Bảo toàn dữ liệu và yêu cầu merge-safe | Không drop/truncate database |
| `services/order-service/src/main/java/com/scentstation/order/entity/*.java` | Chuyển thành JPA entity với `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@Enumerated`, column constraints | Ánh xạ đúng schema và business data | Hỗ trợ persistence và validation |
| `services/order-service/src/main/java/com/scentstation/order/repository/jpa/*.java` | Thiết lập Spring Data JPA adapter | Chuyển từ JDBC thủ công sang JPA repository chính | Không thay đổi API contract |
| `services/order-service/src/test/java/com/scentstation/order/repository/JpaRepositoryIntegrationTest.java` | Thêm kiểm thử JPA mapping + duplicate safeguard | Xác nhận saveIfAbsent, persistence, review update | Tăng độ tin cậy và khả năng merge |

## C. JPA mapping

- Entity: `Order`, `OrderItem`, `Payment`, `PaymentCallbackEvent`, `DispensingCommand`, `TransactionReview`
- Bảng tương ứng: `orders`, `order_items`, `payments`, `payment_callback_events`, `dispensing_commands`, `transaction_reviews`
- Primary key:
  - `Order.id`: `BIGINT AUTO_INCREMENT`
  - `OrderItem.id`: `BIGINT AUTO_INCREMENT`
  - `Payment.reference`: `VARCHAR(64)`
  - `PaymentCallbackEvent.event_id`: `VARCHAR(255)`
  - `DispensingCommand.command_id`: `VARCHAR(36)`
  - `TransactionReview.reference`: `VARCHAR(64)`
- Quan hệ:
  - `Order` -> `OrderItem` (one-to-many, cascade ALL, orphanRemoval)
  - `Payment.order_id` unique, reference order
  - `DispensingCommand.order_id` unique, reference order and payment
- Unique constraints:
  - `payments.order_id` unique
  - `dispensing_commands.order_id` unique
  - `payment_callback_events.event_id` primary key
  - `transaction_reviews.reference` primary key
- Vấn đề schema còn lại: cần thống nhất tenant ID contract với Identity/Catalog trong giai đoạn tích hợp nhóm, nhưng không được đổi kiểu của Order service theo suy đoán.

## D. Cross-service compatibility

- Tenant ID contract: giữ `String` trong Order service, phù hợp với Identity hiện tại dùng UUID String; không tự đổi sang Long vì sẽ phá vỡ contract.
- Machine ID contract: vẫn là `VARCHAR(100)` như schema hiện có.
- Product ID contract: được giữ theo snapshot `Long` trong order item, phù hợp với Catalog đang dùng `Long` product ID.
- Những điểm chưa thống nhất với Catalog/Identity:
  - Tenant identifier type chưa thống nhất giữa Identity, Catalog, Order.
  - Các microservice cần có một quyết định kiến trúc rõ ràng cho ID và tenant context trước khi đưa vào production.
- Đề xuất với thành viên khác: chuẩn hóa tenant context qua API Gateway/Identity, không dùng `tenantId` raw body như dữ liệu demo trong môi trường production.

## E. Test results

Các command đã chạy:
- `mvn clean test -q`
- `mvn verify -q`

Kết quả:
- Test pass: 32 tests, 0 failures, 0 errors, 0 skipped trong lần xác minh sau chỉnh mapping.
- `mvn verify -q` hoàn tất thành công.
- H2 integration: kiểm thử repository-level JPA với H2 MySQL mode.
- Startup với datasource cục bộ đã kết nối được tới MariaDB 10.4.32 (XAMPP), không phải MySQL 8.4. Log ban đầu bắt được schema validation mismatch: database khai báo `currency CHAR(3)` trong khi JPA mong `VARCHAR(3)`.
- Đã sửa mapping cho `currency` của `Order`, `Payment`, và `PaymentCallbackEvent` bằng `columnDefinition = "CHAR(3)"`, giữ nguyên schema và dữ liệu.
- Lần khởi động lại qua Java debugger sau sửa mapping timeout mà không xác nhận session; probe sau đó không thấy listener 8083. Do đó startup hoàn chỉnh và schema validation trên MariaDB chưa được xác minh sau sửa.
- MySQL 8.4 integration chưa chạy: Docker Engine/Desktop không sẵn sàng; cần xác minh thêm khi môi trường MySQL 8.4 có thể chạy.

## F. Git merge readiness

- File đã thay đổi trong phạm vi `services/order-service/`.
- Không sửa file của member khác.
- Không thay đổi root config hay shared configuration.
- Không có migration nguy hiểm hoặc drop table.
- Breaking change: không rõ ràng ở API contract hiện tại; các thay đổi đều giữ contract REST và business behavior cũ.
- Trước khi merge cần kiểm tra lại tenant alignment với Identity/Catalog và xác nhận database schema thực tế trên MySQL nếu môi trường cho phép.

## G. Known risks

- Concurrency: JPA `saveIfAbsent` dựa trên row locking và unique constraints, cần cảnh báo khi mở rộng sang nhiều instance cùng lúc.
- Tenant isolation: `tenant_id` đang phải dựa trên context được truyền từ API Gateway/Identity, không nên tin hoàn toàn vào request body trong môi trường production.
- External service integration: `Machine Service` call trong dispensing flow vẫn là side effect phụ thuộc ngoài service; cần có retry và reconciliation policy rõ ràng.
- Database compatibility: local listener là MariaDB 10.4.32; Docker/MySQL 8.4 và post-fix Hibernate schema validation chưa được xác minh thành công.

## Tổng kết

Order Service hiện đã được chuẩn hóa về Spring Data JPA trong phạm vi module `services/order-service/`, giữ được API contract và business logic cũ, đồng thời có thêm test repository để bảo vệ duplicate-safe behavior, với phát hiện/cảnh báo còn lại chủ yếu ở tenant ownership và cross-service contractalignment.
