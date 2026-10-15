# Identity Service và API Gateway

## Thành phần

- **Identity Service:** Spring Boot, Spring Security, Spring Data JPA, MySQL.
- **Xác thực:** JWT Bearer Token; mật khẩu được lưu dưới dạng BCrypt hash.
- **Vai trò:** `ADMIN`, `STAFF`, `CUSTOMER`. Đăng ký công khai luôn tạo tài khoản `CUSTOMER`; không cho phép người dùng tự đăng ký quyền quản trị.
- **API Gateway:** Sử dụng cổng `8080`, định tuyến các request `/api/auth/**` và `/api/admin/**` đến Identity Service tại cổng `8081`.

## API

- `POST /api/auth/register`: Đăng ký tài khoản.

  ```json
  {
    "username": "demo",
    "email": "demo@example.com",
    "password": "Password123",
    "fullName": "Demo User"
  }
  ```

- `POST /api/auth/login`: Đăng nhập.

  ```json
  {
    "username": "demo",
    "password": "Password123"
  }
  ```

- `GET /api/auth/health`: Kiểm tra trạng thái hoạt động của Identity Service.

Đăng ký và đăng nhập trả về JWT trong trường `token`. Khi gọi API cần xác thực, gửi token qua header:

```http
Authorization: Bearer <token>
```

## Chạy local

1. Cài đặt Java 17 trở lên, Maven và MySQL; bảo đảm MySQL đang chạy.
2. Tạo database `scentstation_identity` hoặc cấu hình JDBC để có thể tạo database nếu được hỗ trợ.
3. Thiết lập các biến môi trường `DB_USERNAME` và `DB_PASSWORD`. Không sử dụng mật khẩu mặc định trong môi trường ngoài local.
4. Trong thư mục `services/identity-service`, chạy:

   ```powershell
   mvn spring-boot:run
   ```

5. Mở terminal khác, chuyển đến thư mục `services/api-gateway` và chạy:

   ```powershell
   mvn spring-boot:run
   ```

6. Gửi request thông qua API Gateway tại `http://localhost:8080`.

## Lưu ý bảo mật

- Thay `JWT_SECRET` bằng secret Base64 ngẫu nhiên, đủ mạnh trong môi trường triển khai.
- Cấu hình CORS theo đúng origin của frontend.
- Tài khoản `ADMIN` và `STAFF` phải được tạo thông qua quy trình quản trị đáng tin cậy. Không cho phép người dùng tự chọn role trong endpoint đăng ký công khai.
- Cấu hình JPA `ddl-auto: update` chỉ phù hợp cho phát triển local. Môi trường thực tế nên sử dụng công cụ migration cơ sở dữ liệu.
# Identity Service và API Gateway

## 1. Thành phần

- **Identity Service:** Sử dụng Spring Boot, Spring Security, Spring Data JPA và MySQL.
- **Xác thực:** Sử dụng JWT Bearer Token; mật khẩu được mã hóa bằng BCrypt trước khi lưu vào cơ sở dữ liệu.
- **Vai trò:** ADMIN, STAFF, CUSTOMER. Đăng ký công khai luôn tạo tài khoản CUSTOMER; tài khoản có quyền cao hơn phải được quản trị viên cấp quyền.
- **API Gateway:** Chạy tại cổng 8080, định tuyến các request `/api/auth/**` và `/api/admin/**` đến Identity Service tại cổng 8081.

## 2. API

- `POST /api/auth/register`: Đăng ký tài khoản.
- `POST /api/auth/login`: Đăng nhập và nhận JWT.
- `GET /api/auth/health`: Kiểm tra trạng thái Identity Service.

Ví dụ dữ liệu đăng ký:

```json
{
  "username": "demo",
  "email": "demo@example.com",
  "password": "Password123",
  "fullName": "Demo User"
}
```

Ví dụ dữ liệu đăng nhập:

```json
{
  "username": "demo",
  "password": "Password123"
}
```

Các API yêu cầu xác thực sử dụng JWT trong header `Authorization: Bearer <token>`.

## 3. Cách chạy local

1. Cài đặt Java 17 trở lên, Maven và khởi động MySQL.
2. Tạo cơ sở dữ liệu `scentstation_identity` hoặc cấu hình quyền tạo cơ sở dữ liệu phù hợp.
3. Thiết lập các biến môi trường `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
4. Thiết lập `JWT_SECRET` bằng secret đủ mạnh và đúng định dạng Base64.
5. Khởi động Identity Service tại cổng 8081 và API Gateway tại cổng 8080.

## 4. Lưu ý bảo mật

- Không sử dụng secret JWT mặc định trong môi trường triển khai thực tế.
- Không sử dụng mật khẩu cơ sở dữ liệu mặc định trong môi trường triển khai thực tế.
- Đăng ký công khai chỉ tạo tài khoản CUSTOMER.
- Kiểm tra quyền truy cập đối với từng vai trò ADMIN, STAFF và CUSTOMER.
- Cấu hình `spring.jpa.hibernate.ddl-auto: update` phù hợp cho phát triển local; cân nhắc sử dụng công cụ migration khi triển khai ổn định.
