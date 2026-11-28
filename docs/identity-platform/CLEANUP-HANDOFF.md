# Bàn giao Identity & Platform

Hoàng Đình Phùng · Người 1 · Nhánh `cleanup/phung-identity-platform`

Baseline `origin/main` tại `327a105`. Nhánh cleanup trước cập nhật ở `38e4e90`, thấp hơn baseline ba commit và được fast-forward. Stash `89f57f7` vẫn lưu thay đổi trước đó. Bốn tệp từng bị xóa trong Mock Payment đều có trên baseline và không tạo diff trong cleanup. Kiểm kê 51 nhánh và phần chức năng lịch sử đã đối chiếu nằm trong [báo cáo nhánh](BRANCH-INVENTORY.md).

## Kiểm thử

Maven 3.9.9 và Java 21 chạy trong container dùng một lần, 2 CPU và 2 GiB. Source được mount chỉ đọc rồi sao chép sang thư mục test. Runner không gắn Docker socket nên các bài tích hợp dùng Testcontainers được ghi nhận là bỏ qua.

| Hạng mục | Baseline | Sau cleanup |
|---|---|---|
| Identity Service | 49 đạt, 2 lỗi ở `AccessPolicyTest`, 2 bỏ qua | 53 đạt, 0 lỗi, 2 bỏ qua |
| API Gateway | 15 đạt, 0 lỗi, 1 bỏ qua | 16 đạt, 0 lỗi, 1 bỏ qua |
| Smoke trên stack có sẵn | 4/5 phép kiểm đạt. Gateway và Identity health trả 200, `/api/auth/me` thiếu token trả 401, POST introspection qua Gateway trả 404. `GET /api/auth/tenants` trả 401 thay vì 200. | Chưa triển khai source cleanup vào stack demo để giữ nguyên dữ liệu và container hiện hành. |
| Cấu hình Compose | `docker compose config --quiet` thành công | Không có diff Compose. |
| Frontend | Admin Web có lỗi có sẵn ở ca `MACHINE_CONFIGURE`. | Admin Web 7/8, Kiosk 4/4, Mock Payment 4/4 và build thành công trong bản sao tạm. |

JWT cũ thiếu `userId` sẽ yêu cầu đăng nhập lại sau khi triển khai. Khi đang chờ duyệt, màn hình người dùng nhận vai trò hiệu lực `STAFF`. Vai trò được đề nghị vẫn nằm trong danh sách quản trị để xử lý. Một tài khoản đã được duyệt cần đăng nhập lại để nhận phiên mới.

Giới hạn đang cấu hình cho Gateway, Identity, Identity MySQL và Redis lần lượt là 512, 512, 384 và 192 MiB (tổng 1.600 MiB). Đây là giới hạn Compose của bốn thành phần, chưa phải benchmark tải cho toàn hệ thống. Runner sử dụng profile kiểm thử riêng với giới hạn 2 GiB và chạy hai module tuần tự. [Lệnh chạy demo và test](../identity-platform.md#demo-và-kiểm-thử).

## Danh sách file

| Thao tác | File | Lý do |
|---|---|---|
| Xóa | `build_identity_platform.ps1` | Script tạo lại source từ bản cũ, yêu cầu đường dẫn và nhánh hard-code. Tìm trong baseline không còn điểm gọi. |
| Thêm | `scripts/Test-IdentityPlatform.ps1` | Một lệnh chạy Maven cô lập và kiểm tra các endpoint trên stack đang chạy. |
| Sửa | `README.md` | Thêm liên kết hướng dẫn Người 1. |
| Thêm | `docs/api-conventions.md` | Tích hợp nội dung nhánh quy ước cũ theo hợp đồng hiện hành. |
| Sửa | `docs/identity-platform.md` | Cập nhật Java 21, API, JWT và quy trình phê duyệt. |
| Sửa | `docs/redis/failure-modes.md` | Phân biệt lỗi trước và sau khi Gateway chuyển tiếp. |
| Sửa | `architecture/identity-service/sequence-diagram.puml` | Cập nhật JWT, trạng thái tenant trực tiếp và phê duyệt. |
| Thêm | `docs/identity-platform/BRANCH-INVENTORY.md` | Kiểm kê 51 nhánh và đối chiếu phần còn khác. |
| Thêm | `docs/identity-platform/CLEANUP-HANDOFF.md` | Báo cáo thay đổi và kết quả kiểm thử. |

### Identity Service

Các đường dẫn dưới đây tương đối với `services/identity-service`. Các tên file Java là duy nhất trong module.

| Thao tác | File | Lý do |
|---|---|---|
| Sửa | `README.md` | Thay mô tả chưa triển khai bằng chức năng và tài liệu đang dùng. |
| Sửa | `pom.xml` | Gỡ starter Spring Cache và thuộc tính Spring Cloud không dùng. |
| Sửa | `IdentityServiceApplication.java` | Gỡ `@EnableCaching` vì cache hồ sơ dùng Redis template trực tiếp. |
| Sửa | `src/main/resources/application.yml` | Gỡ cấu hình Spring Cache rỗng, giữ cấu hình Redis đang dùng. |
| Sửa | `AccountAdminService.java` | Xóa vai trò đề nghị, grants và scopes khi từ chối. |
| Sửa | `JwtAuthenticationFilter.java` | Xác minh một lần, so khớp ID tài khoản và truy vấn trạng thái tenant. |
| Sửa | `User.java` | Thu hồi JWT khi cờ chờ duyệt chuyển trạng thái. |
| Sửa | `GlobalExceptionHandler.java` | Gỡ import không dùng. |
| Sửa | `AccessPolicy.java` | Khôi phục ràng buộc kỹ thuật viên phải có máy cụ thể được phân công. |
| Sửa | `AccountPrincipal.java` | Chỉ cấp vai trò hiệu lực `STAFF` cùng quyền và phạm vi rỗng trong khi chờ duyệt. |
| Sửa | `UserRepository.java` | Khóa bản ghi khi nhận đề nghị phân vai để tuần tự hóa yêu cầu cùng tài khoản. |
| Sửa | `AuthService.java` | Chỉ nhận yêu cầu phân vai từ nhân viên đang hoạt động và chưa gán tenant. Hợp nhất kiểm tra tenant và constructor. |
| Sửa | `JwtService.java` | Dùng lại parser, gắn ID tài khoản, thống nhất vai trò trong JWT chờ duyệt. |
| Sửa | `RedisTenantProfileCache.java` | Kiểm tra ID trong snapshot khi đọc từ cache. |
| Sửa | `SecurityAuthorizationTest.java` | Dùng hợp đồng đọc JWT một lần và trạng thái tenant theo projection. |
| Sửa | `InternalIntrospectionSecurityTest.java` | Kiểm tra phiên có ID tài khoản khác và giữ các ca stale, lock, tenant suspend. |
| Sửa | `RolePermissionsTest.java` | Kiểm tra quyền và phạm vi rỗng khi chờ duyệt cùng việc thu hồi phiên lúc duyệt. |
| Sửa | `AuthServiceTest.java` | Kiểm tra nhân viên đã gán vai trò không thể gửi yêu cầu đổi tenant. |
| Sửa | `JwtServiceSecurityTest.java` | Kiểm tra JWT thiếu phiên bản qua hợp đồng mới. |

### API Gateway

Các đường dẫn dưới đây tương đối với `services/api-gateway`.

| Thao tác | File | Lý do |
|---|---|---|
| Sửa | `README.md` | Thay mô tả placeholder bằng đường dẫn và chức năng hiện tại. |
| Sửa | `pom.xml` | Gỡ Spring Cache starter không có người dùng. |
| Sửa | `ApiGatewayApplication.java` | Gỡ `@EnableCaching` vì rate limiter dùng Redis reactive trực tiếp. |
| Sửa | `src/main/resources/application.yml` | Gỡ phần Spring Cache dư thừa, giữ cấu hình Redis và rate limit. |
| Sửa | `GatewayRateLimitFilter.java` | Giữ 503 cho lỗi limiter, chuyển lỗi downstream qua cơ chế Gateway bình thường. |
| Sửa | `GatewayRateLimitFilterTest.java` | Kiểm tra limiter chỉ xử lý lỗi trước khi chuyển tiếp. |
