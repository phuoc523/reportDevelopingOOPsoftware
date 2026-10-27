# P1-05 — Cô lập dữ liệu Multi-Tenant (Identity Service)

## Phạm vi hoàn thành trong PR này

1. Lấy `tenantId` từ `AuthDto.Identity` do `SessionService.resolve(opaqueToken)` xác thực trên MySQL; **không** lấy từ frontend, query, header hay body làm nguồn quyền.
2. `TenantScopeService.requireTenant()` kiểm tra chính sách tenant và chỉ cho phép ngoại lệ `PLATFORM_SUPER_ADMIN` (đã xác thực) theo quy tắc P1-04. Tài khoản thường không có tenant bị từ chối.
3. `GET /api/v1/tenant-scope/users/{userId}` sử dụng **truy vấn DB có điều kiện tenant**, trả **404** nếu cố đọc user thuộc tenant khác; viewer thiếu `USER_READ` trả **403**.
4. Ngăn `tenantId` giả mạo trên header, query hoặc JSON body ở các API minh họa P1-05 (HTTP 403).
5. P1-04 vẫn kiểm tra tenant của **user đích từ DB** trước khi cấp/thu hồi role; test P1-05 kiểm chứng đường ghi này.
6. Internal token introspection chỉ dành cho service-to-service, từ chối khi không cấu hình khóa hoặc khóa sai; trả kết quả xác thực mới nhất từ opaque token và quyền RBAC.

## API trong identity-service (HTTP :8081)

| Method | Path | Bảo vệ | Kết quả |
| --- | --- | --- | --- |
| GET | `/api/v1/tenant-scope/me` | Bearer hợp lệ | 200 `userId`, `username`, `tenantId`, `roles` từ session |
| GET | `/api/v1/tenant-scope/check?tenantId={id}` | Bearer hợp lệ | 204 scope hợp lệ; 403 sai tenant |
| POST | `/api/v1/tenant-scope/check` | Bearer hợp lệ, JSON `{ "tenantId": "..." }` | 204 hợp lệ; 403 giả mạo |
| GET | `/api/v1/tenant-scope/users/{userId}` | Bearer + `USER_READ` | 200 đúng tenant; 404 không thấy user/cross tenant; 403 thiếu quyền |
| POST | `/api/v1/internal/tenant/introspect` | Header **`X-Internal-Service-Key`** + body `{ "accessToken": "..." }` | 200 thông tin đã xác thực; 401 khóa/token sai; 503 nếu chưa cấu hình |

Các API `/tenant-scope/check` **chỉ kiểm tra scope**, không thay thế kiểm tra quyền nghiệp vụ và không tự chặn truy cập trong Catalog/Order/Inventory/Machine/Reporting.

## Hợp đồng tích hợp cho các microservice

### Xác thực identity giữa các service

- P1-03 cấp **opaque Bearer token**, KHÔNG phải JWT. Mỗi service cần gọi Identity qua kết nối nội bộ tin cậy (HTTPS/mTLS hoặc mạng riêng với TLS) để introspect token, hoặc một cơ chế auth tương đương đã thống nhất.
- Internal request: `POST /api/v1/internal/tenant/introspect`, header `X-Internal-Service-Key: <secret-tối-thiểu-32-ký-tự>`, body JSON `{ "accessToken": "opaque-user-token" }`. Không gửi service key từ trình duyệt.
- Set `TENANT_INTROSPECTION_KEY` **chỉ trên Identity và backend service tin cậy** qua secret manager/biến môi trường; không commit và không log. Mặc định không cấu hình → HTTP 503 (fail closed).
- **Không publish endpoint internal qua public API Gateway**. Chặn bằng routing và mạng, rate limit và monitor. Shared secret chỉ là lớp xác thực dịch vụ tối thiểu cho môi trường đồ án; production nên chuyển sang mTLS/service identity, key rotation, replay controls.
- Response 200 gồm `{userId, username, tenantId, roles, permissions}`; được tính từ **session và DB hiện tại**, không lấy tenantId do frontend gửi.
- Token hết hạn/thu hồi → HTTP 401; backend phải từ chối request tương ứng, không fallback sang tenantId/header tự khai.
- Trong bản đầu, **không cache** introspection vượt vòng đời request (để revoke hiệu lực ngay). Nếu cache về sau phải có TTL ngắn và policy rõ ràng.

### Quy tắc tenant bắt buộc tại service sở hữu tài nguyên

1. Mỗi request được xác thực, lấy `verifiedTenantId` từ response tin cậy; mọi `tenantId` trong URL/query/header/body **chỉ là giá trị yêu cầu**, phải đối chiếu với verified tenant. Đừng sử dụng `X-Tenant-ID` như bằng chứng identity.
2. Khi đọc theo `resourceId`, truy vấn có điều kiện tenant cùng lúc: `SELECT ... WHERE id = :id AND tenant_id = :verifiedTenantId`; không query global rồi mới check. Có thể trả 404 khi tài nguyên thuộc tenant khác để tránh tiết lộ sự tồn tại.
3. Khi thay đổi: `UPDATE ... SET ... WHERE id = :id AND tenant_id = :verifiedTenantId`, kiểm tra số dòng bị tác động; bên cạnh đó kiểm tra domain RBAC permission và business rules. Khi insert, `tenant_id` phải do backend đặt từ context đã xác thực, không copy từ JSON frontend.
4. Tài nguyên thuộc quan hệ cha/con: bắt buộc xác minh owner tenant qua join/foreign key cha trước khi tạo/sửa con (đặc biệt order-items, inventory transactions, machines).
5. `PLATFORM_SUPER_ADMIN`: chỉ là ngoại lệ xuyên tenant khi policy của endpoint cho phép rõ ràng, kèm audit đầy đủ; không được tự động bypass mọi quy tắc nghiệp vụ.
6. Các event/messages async: mang `tenantId` được gắn tại service đã xác thực, kèm định danh producer qua kênh tin cậy; consumer kiểm tra quyền truy cập vào resource trước khi xử lý.
7. Không log raw Bearer token, service key, hay password. Cấp quyền/revoke và truy cập dữ liệu nhạy cảm cần audit.

### Owner theo service

| Service | Quy tắc bắt buộc |
| --- | --- |
| `catalog-service` | Filter sản phẩm/menu/cấu hình brand theo `tenant_id` ở query và update |
| `order-service` | Verify tenant của order trước read/cancel/refund; trạng thái và quyền refund được kiểm tra riêng |
| `operation-service` (Inventory) | Stock, adjustments, audit movements gắn tenant từ máy/kho thuộc brand |
| `machine-service` | Xác thực tenant sở hữu machine trước đọc/cấu hình/diagnostic spray; device commands qua kênh tin cậy |
| `reporting-service` | Aggregations luôn lọc tenant trong truy vấn, tránh lộ dữ liệu qua join/group, cache và export |
| `api-gateway`, frontend | Không tin/không chuyển tiếp các header danh tính tự khai (X-Tenant-Id, X-User-Role) như một quyền đã xác minh |

**Giới hạn rõ ràng:** Patch này chỉ sửa `identity-service`. Nó chưa thể chứng minh dữ liệu Catalog/Order/Inventory/Machine/Reporting đã cô lập, vì đó là mã nguồn do các thành viên khác sở hữu. Nhóm phải hoàn thành các guard tương ứng trước khi đánh dấu nghiệm thu toàn hệ thống.

## Kiểm thử

`TenantScopeIntegrationTest` có 5 case: giả mạo tenant trong header/query/body; kiểm tra đọc DB theo tenant; ghi cross-tenant qua P1-04; ngoại lệ platform theo chính sách; nội bộ introspection + token revoke.

Chạy từ `services/identity-service`:

```powershell
mvn "-Dmaven.repo.local=E:\XD\.m2\repository" "-DforkCount=0" test
```

Live smoke trên MySQL đang chạy (nhập password `demo.admin` qua secure prompt):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-p1-05-live.ps1
```

Tất cả test cũ của P1-03/P1-04 phải tiếp tục PASS. Không commit trước khi kiểm thử thành công.
