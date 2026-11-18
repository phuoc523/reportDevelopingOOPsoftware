# Task 3 – Configure MySQL Connectivity

## Mục tiêu

Thiết lập sẵn khả năng kết nối Apache NiFi với MySQL trong môi trường Docker hiện có, đồng thời chuẩn bị cấu hình `DBCPConnectionPool` và vị trí driver JDBC theo đúng phạm vi của Task 3. Mục tiêu này là chuẩn bị kết nối, không thay đổi logic nghiệp vụ hoặc database của các service.

## Phạm vi và ràng buộc

- Chỉ làm việc trong `infra/nifi/` và `docs/nifi/` theo quy định.
- Không thay đổi dữ liệu, schema, service, API hoặc Docker Compose của các microservice.
- Không ghi mật khẩu thật vào file, log hoặc tài liệu.
- Không chạy `docker compose down -v`.
- Không tự ý xác định database nguồn/đích nếu Task 1 chưa xác nhận; nếu thông tin chưa đủ, cấu hình sẽ được giữ ở dạng tham số hóa và chờ phê duyệt.

## Kết luận khảo sát hiện trạng

### 1) Apache NiFi hiện đang chạy theo Docker Compose riêng

File `infra/nifi/docker-compose.yml` đang dùng:

- Project name: `scentstation-nifi`
- Service: `nifi`
- Image: `apache/nifi:2.4.0`
- Port Web UI: `127.0.0.1:8443:8443`
- Single user authentication đang được cấu hình qua biến môi trường `NIFI_USERNAME`/`NIFI_PASSWORD`

### 2) MySQL đang chạy trong Compose chính của dự án

Theo cấu hình hiện có ở `docker-compose.yml`, các MySQL database được triển khai trong project `scentstation-local` với các service tên:

- `catalog-db`
- `order-db`
- `reporting-db`
- `identity-db`
- `machine-db`
- `operation-db`
- `notification-db`

Các service MySQL đang nằm trong network Docker mặc định của project `scentstation-local`, tương ứng với tên network:

- `scentstation-local_default`

Do NiFi đang chạy trong project `scentstation-nifi`, container NiFi không thể tự động giải tên host như `catalog-db` hoặc `order-db` nếu không gắn thêm network `scentstation-local_default`. Vì thế phần cấu hình Docker trong `infra/nifi/docker-compose.yml` đã được bổ sung để gắn `nifi` vào network hiện có của project chính.

### 3) Chưa xác định nguồn/đích cuối cùng

Theo tài liệu Task 1, phạm vi nguồn/đích chưa được phê duyệt chắc chắn cho luồng NiFi tương lai. Vì vậy, task này không hardcode một URL MySQL cụ thể vào file cấu hình hoặc tài liệu. Thay vào đó, task chuẩn bị các tham số theo template, đồng thời lưu ý rằng host, database, user và secret phải được xác nhận theo quyết định cuối cùng của Task 1 trước khi vận hành flow thật.

## Thay đổi đã thực hiện trong `infra/nifi/`

### 1) Bổ sung cấu hình network chia sẻ với MySQL đang chạy

Trong `infra/nifi/docker-compose.yml`, service `nifi` đã được thêm:

- mount thư mục driver JDBC tại `/opt/nifi/nifi-current/drivers`
- gắn network `scentstation-local_default` ngoài network riêng của project NiFi

Điều này cho phép NiFi truy cập vào các host Docker như `catalog-db` và `order-db` khi database đã được xác nhận và quyền truy cập được cấp đúng cách.

### 2) Chuẩn bị thư mục driver JDBC

Thư mục `infra/nifi/drivers/` đã được tạo để lưu MySQL JDBC driver. File JAR thực tế không được kiểm tra vào Git vì đây là tài liệu và cấu hình không chứa secret; người vận hành phải tải driver từ nguồn chính thức theo hướng dẫn dưới đây.

### 3) Bổ sung placeholder trong `.env.example`

File `infra/nifi/.env.example` đã được bổ sung một số biến mẫu để chuẩn hóa việc hỗ trợ MySQL connectivity trong tương lai:

- `MYSQL_HOST=host.docker.internal`
- `MYSQL_PORT=3306`
- `MYSQL_DRIVER_VERSION=8.4.0`

Cách sử dụng:

- `NIFI_USERNAME` và `NIFI_PASSWORD` vẫn là biến bắt buộc cho Single User Authentication của NiFi.
- `MYSQL_*` là biến tham khảo triển khai, không chứa thông tin xác thực thật và không dùng cho cấu hình ưu tiên trong UI khi đã có secret manager.

## Driver JDBC phù hợp

### Phiên bản khuyến nghị

Apache NiFi 2.4.0 đang chạy trên Java hiện đại; MySQL Connector/J phù hợp nhất để dùng trong môi trường này là phiên bản 8.4.x.

Ví dụ driver chuẩn:

- `mysql-connector-j-8.4.0.jar`

### Nguồn tải chính thức

Tải từ Maven Central theo链接 chính thức:

- `https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar`

Sau khi tải, lưu vào đường dẫn:

- `infra/nifi/drivers/mysql-connector-j-8.4.0.jar`

Không lưu trữ file JAR không rõ nguồn gốc hoặc file được copy từ máy khác.

## Cách cấu hình trong Apache NiFi

Sau khi file JAR đã có tại `/opt/nifi/nifi-current/drivers` trong container, thực hiện trong NiFi UI:

1. Vào `Controller Settings`.
2. Chọn `Controller Services`.
3. Thêm `DBCPConnectionPool`.
4. Thiết lập các thuộc tính sau:

- `Database Connection URL`: `jdbc:mysql://<approved-db-host>:3306/<approved-database>?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC`
- `Database Driver Class Name`: `com.mysql.cj.jdbc.Driver`
- `Database Driver Location(s)`: `file:///opt/nifi/nifi-current/drivers/mysql-connector-j-8.4.0.jar`
- `Database User`: `<approved-db-user>`
- `Password`: `<approved-db-password>`
- `Validation Query`: `SELECT 1`

Lưu ý quan trọng:

- Nếu database được cấp quyền là `catalog-db`, `order-db` hoặc `reporting-db`, nên sử dụng hostname Docker tương ứng trong Compose hiện tại: `catalog-db`, `order-db`, `reporting-db`.
- Nếu vì mục đích triển khai chạy ở máy host không dùng Docker network nội bộ, có thể dùng `host.docker.internal` cùng với `MYSQL_PORT=3306`, nhưng cách này phải được xác nhận với môi trường thực tế và không được coi là mặc định khi project đã có network Docker sẵn.
- Mật khẩu và thông tin xác thực phải được lưu trong secret provider của NiFi hoặc biến môi trường được quản lý an toàn; không gõ trực tiếp vào file, log hoặc tài liệu.

## Kiểm tra trạng thái controller service

Sau khi `DBCPConnectionPool` được thêm và driver đã được đăng ký:

1. Trong NiFi UI, bật Controller Service.
2. Kiểm tra status để đảm bảo `Valid` hoặc `Enabled`.
3. Nếu service đang báo lỗi, cần kiểm tra:
   - đúng class name `com.mysql.cj.jdbc.Driver`
   - đường dẫn JAR đúng trên container
   - database hostname và port khớp với network hiện tại
   - tài khoản database có quyền truy cập truy vấn tối thiểu

## Thực hiện truy vấn SQL thử nghiệm

Sau khi controller service được kích hoạt, nên chạy một truy vấn đơn giản dạng:

```sql
SELECT 1;
```

Nếu cần kiểm tra schema cụ thể và database đã được xác nhận, ví dụ:

```sql
SHOW TABLES;
```

Hoặc:

```sql
SELECT COUNT(*) FROM <approved-table-name>;
```

Cách này đảm bảo NiFi không chỉ đăng ký driver mà còn thực sự nhận dữ liệu từ MySQL.

## Kiểm tra thực thi và xác nhận kết nối

Các bước kiểm tra khuyến nghị:

1. `docker compose --env-file .\infra\nifi\.env -f .\infra\nifi\docker-compose.yml config`
2. `docker compose --env-file .\infra\nifi\.env -f .\infra\nifi\docker-compose.yml up -d`
3. `docker compose --env-file .\infra\nifi\.env -f .\infra\nifi\docker-compose.yml exec nifi ls -l /opt/nifi/nifi-current/drivers`
4. Đăng nhập NiFi UI và xác minh Controller Service `DBCPConnectionPool` có status `Valid`
5. Chạy truy vấn thử nghiệm SQL để xác nhận DBCP có thể đọc dữ liệu từ MySQL

## Lưu ý về nguồn/đích cuối cùng

Hiện tại, dữ liệu nào phải là nguồn, dữ liệu nào phải là đích, và bảng nào cần đồng bộ vẫn đang ở trạng thái chờ phê duyệt theo Task 1. Vì vậy, tiêu chí chính của Task 3 là:

- NiFi có thể nhận diện driver MySQL
- NiFi có thể kết nối đúng database đã được phê duyệt
- DBCPConnectionPool được cấu hình đúng chuẩn để có thể hoạt động ngay khi Task 1 xác định nguồn/đích cụ thể

Không triển khai một flow đồng bộ thật cho đến khi các quyết định dữ liệu đã được xác nhận rõ ràng.

## Kết quả kiểm tra đã đạt được

- Cấu trúc `infra/nifi/drivers/` đã được chuẩn bị để lưu JDBC driver.
- Docker Compose NiFi đã được cập nhật để gắn network với `scentstation-local_default`.
- `docker-compose.yml` sẽ cho phép container NiFi truy cập đến MySQL service khi tên database và quyền truy cập đã được xác nhận.
- `DBCPConnectionPool` được chuẩn bị theo template với `com.mysql.cj.jdbc.Driver`, path `file:///opt/nifi/nifi-current/drivers/...`, và validation query `SELECT 1`.

Bản chất của Task 3 tại thời điểm này là chuẩn bị kết nối kỹ thuật, không đồng bộ dữ liệu sản xuất. Tất cả quyết định nghiệp vụ về nguồn/đích phải được Task 1 xác nhận trước khi đưa flow vào vận hành thực tế.
