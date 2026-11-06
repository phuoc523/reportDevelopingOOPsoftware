# P1-04 — RBAC đa vai trò (Identity Service)

## Kiến trúc

- Kế thừa P1-03: opaque bearer token, session lưu trong MySQL, `StaffUser.roles` lưu tập hợp nhiều role.
- Quyền là **union** các permission của role nhận diện được, **deny by default** với role lạ.
- Mỗi request `/api/v1/rbac/**` đều cần bearer token hợp lệ; method-level guard `@PreAuthorize` bảo vệ APIs quản trị.
- Khi cấp/thu hồi role, tất cả session của user đích bị thu hồi trong cùng giao dịch; user phải login lại.
- Phân quyền trên tài nguyên theo tenant: `BRAND_ADMIN` chỉ thao tác trên user thuộc cùng tenant; `PLATFORM_SUPER_ADMIN` được phép xuyên tenant.
- `PLATFORM_SUPER_ADMIN` là role đặc quyền, **không được BRAND_ADMIN cấp hoặc thu hồi**. Cấp quyền platform admin ban đầu phải qua quy trình provision ngoài ứng dụng với kiểm soát riêng.
- Mọi thay đổi role thành công lưu vào bảng `role_assignment_audit` (actor, target, tenant, role, action, timestamp).

## Bảng quyền v1 (đề xuất theo mô tả task; cần nhóm duyệt trước tích hợp domain APIs)

| Role | Quyền |
| --- | --- |
| PLATFORM_SUPER_ADMIN | Tất cả quyền; cho phép xuyên tenant |
| BRAND_ADMIN | USER_READ, ROLE_READ, ROLE_ASSIGN, ROLE_REVOKE, ROLE_AUDIT_READ, ORDER_READ, REFUND_APPROVE, INVENTORY_READ, INVENTORY_ADJUST, MACHINE_READ, MACHINE_DIAGNOSTIC_SPRAY, MACHINE_CONFIGURE, REPORT_READ (chỉ trong tenant) |
| OPERATIONS_MANAGER | ORDER_READ, INVENTORY_READ, MACHINE_READ, REPORT_READ |
| TECHNICIAN | MACHINE_READ, MACHINE_DIAGNOSTIC_SPRAY |
| INVENTORY_STAFF | INVENTORY_READ, INVENTORY_ADJUST |
| VIEWER | ORDER_READ, INVENTORY_READ, MACHINE_READ, REPORT_READ |

**Thao tác nhạy cảm:** REFUND_APPROVE, INVENTORY_ADJUST, MACHINE_DIAGNOSTIC_SPRAY, MACHINE_CONFIGURE. RBAC chỉ là lớp chặn đầu; refund cần policy hạn mức và kiểm tra trạng thái đơn, điều chỉnh tồn kho cần audit transaction, diagnostic spray cần interlock/safety và xác nhận thiết bị, cấu hình máy cần kiểm tra tính hợp lệ và audit cấu hình trong service sở hữu tài nguyên.

## API

Base URL: `http://localhost:8081/api/v1/rbac`

| Method | Path | Result/Permission |
| --- | --- | --- |
| GET | `/me` | 200 role và effective permissions của user hiện tại |
| GET | `/roles` | 200 role-permission catalog; yêu cầu `ROLE_READ` |
| GET | `/check/{permission}?tenantId={tenantId}` | 204 nếu có permission **và đúng tenant**; không đủ quyền 403 |
| PUT | `/users/{userId}/roles/{role}` | 200 cấp role, yêu cầu `ROLE_ASSIGN` + tenant policy; lỗi 403 / 404 / 409 |
| DELETE | `/users/{userId}/roles/{role}` | 200 thu hồi, yêu cầu `ROLE_REVOKE` + tenant policy; lỗi 403 / 404 / 409 |
| GET | `/audit` | 200 tối đa 50 thay đổi gần nhất trong tenant; super admin thấy toàn hệ thống; yêu cầu `ROLE_AUDIT_READ` |

`/check` **chỉ phản hồi quyết định chính sách** và **không thực hiện hoàn tiền, xuất kho hay điều khiển máy**. Tránh dùng nó như một endpoint public làm proxy bypass của API thật.

### Ví dụ PowerShell (demo.admin đã login ở P1-03)

```powershell
$base = "http://127.0.0.1:8081/api/v1"
$secure = Read-Host "demo.admin password" -AsSecureString
$password = [System.Net.NetworkCredential]::new("", $secure).Password
$login = Invoke-RestMethod -Uri "$base/auth/login" -Method POST -ContentType "application/json" -Body (@{username="demo.admin";password=$password}|ConvertTo-Json)
$password = $null
$headers = @{Authorization="Bearer $($login.accessToken)"}
Invoke-RestMethod -Uri "$base/rbac/me" -Headers $headers
Invoke-RestMethod -Uri "$base/rbac/roles" -Headers $headers
Invoke-WebRequest -Uri "$base/rbac/check/REFUND_APPROVE" -Headers $headers -UseBasicParsing | Select-Object StatusCode
```

## Tích hợp microservices khác

Các service `order-service`, `operation-service`, `machine-service` **chưa được bảo vệ tự động** bởi patch này. Chúng phải thực hiện xác thực bearer token bằng **kênh tin cậy service-to-service**, kiểm tra quyền server-side và kiểm tra `tenantId` từ dữ liệu chủ sở hữu tài nguyên. Không tin các header như `X-User-Role` hoặc kết quả `/check` do frontend gửi lên.

Cần nhóm thống nhất hợp đồng nội bộ để kiểm tra token/role/tenant (ví dụ gateway bảo vệ đường gọi và service authorization nội bộ), cùng chính sách thu hồi token, cache và timeout. Vì P1-03 dùng opaque token, các service khác **không thể tự xác minh JWT** nếu không có introspection API tin cậy.

## Chạy kiểm thử

Từ `services/identity-service`, dùng Maven Java 21:

```powershell
mvn "-Dmaven.repo.local=E:\XD\.m2\repository" test
```

Kiểm thử `RbacIntegrationTest` gồm: 401 anonymous, 403 viewer thiếu quyền, grant/revoke + audit + revoke session, deny cross-tenant/privilege escalation, sensitive-action permission matrix, multi-role union và duplicate action 409. Các test P1-03 cũ vẫn phải PASS.

## Giới hạn triển khai

- Bảng quyền là policy code (versioned), chưa phải chức năng UI chỉnh sửa permission tùy ý.
- Không cung cấp API bootstrap `PLATFORM_SUPER_ADMIN` từ account brand admin để tránh leo thang đặc quyền.
- `spring.jpa.hibernate.ddl-auto=update` chỉ cho dev; dùng migration versioned và review DDL trước triển khai production.
- `audit` chỉ lưu hành động cấp/thu hồi thành công; muốn theo dõi cả thao tác bị chặn cần bổ sung security event pipeline.
