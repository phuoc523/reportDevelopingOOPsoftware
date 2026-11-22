# Kiểm thử SePay

Chạy `mvn verify` tại `services/order-service` bằng JDK 21 và `npm run build` tại `frontend/kiosk-web`.

`SepayWebhookIntegrationTest` gọi API qua MockMvc, dùng schema H2 độc lập, kết nối Identity và Machine mô phỏng. Các trường hợp gồm tạo QR, sai khóa webhook, vòng đời đơn, sai tài khoản/số tiền/mã chuyển khoản, giao dịch ghi nợ, khóa trùng và webhook đồng thời.

Kết quả ở môi trường test chỉ xác minh hành vi với dữ liệu kiểm soát. Kiểm thử MySQL thật, restart, kết nối SePay live và ghi có thật cần được chạy ở môi trường được duyệt. Quy trình đối soát callback thiếu hiện dùng giao diện SePay và bảng audit.

## Kết quả ngày 22/11/2026

- Order Service `mvn verify`: build thành công, 71 test đạt, 2 test Testcontainers bỏ qua do môi trường thiếu Docker.
- Kiểm thử SePay qua MockMvc/H2: 6 test đạt, gồm chữ ký sai, timestamp cũ và body bị sửa.
- Kiosk Web `npm run build`: thành công.
- VietQR được xác minh theo URL và tham số sinh bởi backend. Chưa quét bằng ứng dụng ngân hàng.
- Webhook công khai, MySQL triển khai, khởi động lại, thanh toán thật và máy xịt thật: chưa kiểm chứng.
