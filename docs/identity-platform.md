# Identity Service và API Gateway

Identity quản lý tài khoản, JWT, phê duyệt nhân viên, vai trò, phạm vi thương hiệu và nhật ký. Gateway định tuyến API công khai, xử lý CORS và giới hạn lưu lượng. Cấu hình local mặc định dùng Gateway tại cổng 8080 và Identity tại cổng 8081.

## Demo và kiểm thử

Tại thư mục gốc, với Docker Desktop và stack ScentStation đã chạy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-IdentityPlatform.ps1
```

Lệnh chạy Maven với Java 21 trong container giới hạn 2 CPU và 2 GiB, sau đó thử các endpoint chỉ đọc qua Gateway. Dùng `-TestsOnly` nếu stack chưa chạy, hoặc `-SmokeOnly` để kiểm tra riêng stack đang chạy. `-GatewayUrl` cho phép trỏ tới cổng local khác. Testcontainers Redis yêu cầu chạy Maven bên ngoài container trong môi trường có Docker khả dụng. Runner giới hạn bộ nhớ dành cho bước test, còn mức sử dụng của full stack phụ thuộc các profile đang chạy.

## Tài khoản và phiên

| Endpoint | Hành vi |
|---|---|
| `POST /api/auth/register` | Tạo khách hàng và cấp JWT. |
| `POST /api/auth/register-staff-account` | Tạo tài khoản nhân viên chưa gán thương hiệu, với vai trò `STAFF` và quyền rỗng. |
| `POST /api/auth/staff-role-request` | Nhân viên chưa gán gửi một yêu cầu cho thương hiệu đang hoạt động và nhận JWT chờ duyệt. |
| `POST /api/auth/register-staff` | Luồng trực tiếp tạo tài khoản chờ duyệt chưa kích hoạt. |
| `POST /api/auth/login` | Đăng nhập bằng tên và mật khẩu. |
| `GET /api/auth/me` | Trả về vai trò và quyền đang có hiệu lực. |
| `POST /api/auth/change-username`, `change-password`, `logout` | Quản lý thông tin và phiên. |

Quản trị viên xử lý yêu cầu theo các route `approve-registration` và `reject-registration` trên tài nguyên tài khoản. Tài khoản chờ duyệt có vai trò hiệu lực `STAFF`, quyền và phạm vi vận hành rỗng. Quản trị viên vẫn xem được vai trò được đề nghị trong danh sách tài khoản. Khi duyệt, phiên chờ duyệt bị thu hồi và nhân viên đăng nhập lại để nhận quyền. Khi từ chối, tài khoản bị khóa và yêu cầu vai trò, quyền tường minh, phạm vi máy và địa điểm bị gỡ.

JWT được ký bằng secret đã cấu hình. Identity đối chiếu chữ ký, thời hạn, định danh tài khoản và phiên bản phiên với trạng thái hiện hành. Sau khi triển khai, người dùng có JWT cũ thiếu `userId` cần đăng nhập lại một lần. Dữ liệu duyệt, trạng thái khóa, vai trò và phạm vi đến từ Identity. Các dịch vụ nội bộ lấy principal qua `POST /internal/auth/introspect` với bearer token và khóa liên dịch vụ.

## Phạm vi thương hiệu và Redis

Identity đối chiếu trạng thái tenant từ MySQL trên các yêu cầu xác thực có tenant. Cache Redis lưu snapshot hồ sơ hiển thị theo mã định danh tenant. Gateway dùng Redis để đếm yêu cầu theo principal đã xác minh hoặc địa chỉ kết nối cho người dùng ẩn danh. Xem [hành vi của Gateway](redis/gateway.md), [quy ước API](api-conventions.md) và [UML Identity](../architecture/identity-service/README.md).
