# ScentStation và SePay cho MB Bank

Kiosk tạo đơn dùng thử qua Order Service như trước. Backend lấy tổng tiền VND đã tính cho đơn, cấp mã `SS` và 20 ký tự hex, rồi trả URL ảnh VietQR theo định dạng SePay. Kiosk hiển thị ảnh và theo dõi trạng thái qua API đã xác thực. Mã cũ bắt đầu bằng `PAY-` tiếp tục là thanh toán mô phỏng.

Webhook ghi sự kiện và đối soát trong một transaction. Nếu khớp, transaction lưu thanh toán, đơn đã xác nhận và một lệnh xịt. Backend gửi lệnh sau commit. Nếu tiến trình dừng giữa commit và lần gửi, lệnh vẫn tồn tại để rà soát. Không tự gửi lại khi chưa biết kết quả ở Machine Service.

Bản này chỉ nhận thanh toán SePay cho đơn dùng thử. API từ chối tạo VietQR cho đơn mua chai. Cần bổ sung quy trình giữ hàng và xử lý kết quả đối với đơn mua chai trước khi mở thanh toán thật cho loại đơn đó.

| Biến môi trường của Order Service | Giá trị |
| --- | --- |
| PAYMENT_PROVIDER | `MOCK` mặc định, hoặc `SEPAY` |
| PAYMENT_BANK_ACCOUNT | Số tài khoản MB đã kết nối, nhập cục bộ |
| PAYMENT_BANK_ACCOUNT_NAME | Tên chủ tài khoản đúng theo ngân hàng |
| SEPAY_WEBHOOK_SECRET | Khóa ký webhook HMAC do quản trị viên cấu hình, tối thiểu 32 ký tự in được |

Docker Compose gốc truyền các biến cấu hình này. Khi chạy không Docker, đặt biến trong terminal trước khi chạy `mvn spring-boot:run` tại `services/order-service` bằng JDK 21. Dùng MySQL local dành riêng cho test và cấu hình các dependency Identity, Catalog, Machine theo README của repo. Sau khi Order Service và API Gateway sẵn sàng, chạy `npm ci` và `npm run dev` trong `frontend/kiosk-web`.

Order Service tạo bổ sung bảng `sepay_credit_events` bằng SQL init. Xác minh quyền và duyệt script trên bản sao MySQL trước khi chạy trên DB đang phục vụ khách.

### Khôi phục webhook bị lỡ

Order Service có job đối soát SePay API v2 mỗi 15 phút, quét lại 24 giờ gần nhất và dùng cùng luồng xác nhận đơn với webhook. Job mặc định tắt; khi vận hành cần cấu hình `SEPAY_RECONCILIATION_ENABLED=true`, `SEPAY_API_TOKEN` (API token chỉ lưu ở secret store) và `SEPAY_BANK_ACCOUNT_ID` (UUID tài khoản MB trong SePay). Job chỉ nhận giao dịch tiền vào có số tài khoản khớp `PAYMENT_BANK_ACCOUNT`; mã giao dịch UUID và mã tham chiếu được chống trùng với webhook.

API v2 trả `id` dạng UUID. Với database đã tồn tại dùng cột `transaction_id BIGINT`, hãy sao lưu và kiểm thử bản sao trước khi DBA chạy `services/order-service/database/migrate-sepay-event-ids-to-uuid.sql`. Không chạy migration này tự động trong lúc khởi động ứng dụng. Bảng mới sẽ được tạo với kiểu tương thích UUID qua SQL init.

Đối soát chỉ khôi phục giao dịch có nội dung/mã thanh toán phù hợp, đúng tài khoản, đúng số tiền và đơn còn hiệu lực; các giao dịch khác được ghi lại là chưa khớp hoặc bỏ qua khi không thuộc tài khoản này. API token và HTTPS endpoint vẫn cần được cấp/cấu hình tại môi trường triển khai.

- [Webhook setup](SEPAY_WEBHOOK_SETUP.md)
- [Security checklist](SEPAY_SECURITY_CHECKLIST.md)
- [Test report](SEPAY_TEST_REPORT.md)
- [Định dạng URL ảnh QR của SePay](https://docs.sepay.vn/tao-qr-code-vietqr-dong.html)
