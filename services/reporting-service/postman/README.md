# Kiểm tra Reporting bằng Postman

Không cần Admin Web. Import `ReportingBackend.postman_collection.json` vào Postman.
Collection chứa 16 request; chưa chứa tài khoản, token hoặc API key thực tế.

## Chuẩn bị URL và tài khoản

Trong Collection Variables, điền các giá trị tại máy:

| Biến | Ý nghĩa |
|---|---|
| `reportingUrl` | Reporting trực tiếp, ví dụ `http://localhost:8086`; hoặc Gateway cho API công khai |
| `identityUrl` | Identity trực tiếp, hoặc URL Gateway có route `/api/auth/**` |
| `username`, `password` | Tài khoản Identity thật đã được cấp |
| `tenantId` | Tenant số dương cần xem, phải phù hợp quyền tài khoản |
| `startDate`, `endDate` | Khoảng ngày ISO, bao gồm cả hai ngày |
| `reference` | Payment reference đang tồn tại trong Order |
| `internalApiKey` | Cùng khóa đang cấu hình ở Order và Reporting; chỉ cần khi replay |

Không chọn chạy toàn bộ collection một cách mặc định: folder replay ghi dữ liệu
Reporting, và logout thu hồi các token hiện có của tài khoản đó. Khi chia sẻ/export
collection, xóa các giá trị credential/token/key đã nhập tại máy.

## Thứ tự chạy

1. Gọi **Reporting health**; kỳ vọng 200, không cần token.
2. Gọi **Identity login** bằng username/password thật. Khi thành công, script lưu
   token vào biến `accessToken` của collection; không in token ra console.
3. Gọi **Identity verified profile** để kiểm tra tài khoản, tenant, roles và scopes.
4. Tài khoản có REPORT_READ và không bị giới hạn máy/vị trí: gọi dashboard.
   Tài khoản tenant có thể bỏ `tenantId` để server tự lấy từ Identity. Tài khoản
   platform cần chỉ định tenant nếu muốn đọc Machine/Operation.
5. Kiểm tra `access` và `upstream` trước khi đọc các số liệu. `null` hoặc nguồn lỗi
   không được hiểu là dữ liệu đã xác nhận bằng 0.
6. Chỉ dùng nhóm financial/export với tài khoản platform phù hợp. Dùng **Send and
   Download** cho Excel, lưu đuôi `.xlsx`.
7. Nếu cần khôi phục một payment bị bỏ lỡ, dùng request replay với Reporting trực
   tiếp và đúng API key. Gửi lại cùng dữ liệu phải giữ cùng ID giao dịch.
8. Logout khi muốn kiểm tra thu hồi token; đăng nhập lại để tiếp tục các request.

## Phân biệt quyền

| Request | Điều kiện |
|---|---|
| Health | Công khai |
| Dashboard tenant | REPORT_READ, đúng tenant, chưa hỗ trợ account có allowlist máy/vị trí |
| Dashboard platform | REPORT_READ; có PLATFORM_REPORT_READ mới kèm tài chính toàn hệ thống |
| Search/summary/revenue/refunds | PLATFORM_REPORT_READ |
| Export | REPORT_EXPORT và PLATFORM_REPORT_READ |
| Replay nội bộ | X-Internal-Api-Key, không cần Bearer; Gateway không route endpoint này |

CUSTOMER được tạo bằng public registration không tự có quyền đọc báo cáo.
Không dùng role selector hoặc tự thêm `X-Verified-*` để cấp quyền. Cần member Identity
cấp tài khoản phù hợp qua cơ chế quản trị thật; không sửa role trực tiếp trong DB.

## Mã phản hồi cần kiểm tra

- 400: ngày/filter/payload không hợp lệ.
- 401: thiếu/sai/thu hồi Bearer, hoặc sai API key trên endpoint nội bộ.
- 403: thiếu quyền, sai tenant hoặc scope chưa được hỗ trợ.
- 404: không tìm thấy giao dịch/payment hoặc URL không được route.
- 409: dữ liệu xung đột, trạng thái cũ hoặc tranh chấp đồng bộ; kiểm tra rồi replay.
- 502: dữ liệu/phản hồi upstream không hợp lệ; luồng đọc Order cũng dùng 502 khi kết nối lỗi.
- 503: không thể xác minh Identity; request bị từ chối, không chuyển sang đăng nhập mô phỏng.

## Chạy backend trực tiếp

Reporting cần MySQL có cấu hình `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, cùng URL
Identity/Order/Machine/Operation đúng môi trường. Đọc cấu hình trong
`src/main/resources/application.yml`; không mặc định các service đã chạy.

```powershell
mvn -f .\services\reporting-service\pom.xml clean verify
java -jar .\services\reporting-service\target\reporting-service-0.0.1-SNAPSHOT.jar
```

Chạy từ thư mục gốc repo sau khi thiết lập biến môi trường tại máy. Đổi
`REPORTING_SERVICE_PORT` nếu cổng mặc định bị chiếm, rồi cập nhật `reportingUrl`.
Không cần sửa Compose để chạy Reporting bằng JAR với các upstream URL truy cập được.
Các database/container không publish cổng ra host cần một cấu hình kết nối local
được nhóm thống nhất; collection không tự mở cổng hoặc thay đổi hạ tầng.
