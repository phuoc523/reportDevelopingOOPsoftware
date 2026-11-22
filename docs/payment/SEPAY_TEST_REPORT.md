# Kiểm thử SePay

Chạy `mvn verify` tại `services/order-service` bằng JDK 21 và `npm run build` tại `frontend/kiosk-web`.

`SepayWebhookIntegrationTest` gọi API qua MockMvc, dùng schema H2 độc lập, kết nối Identity và Machine mô phỏng. Các trường hợp gồm tạo QR, sai khóa webhook, vòng đời đơn, sai tài khoản/số tiền/mã chuyển khoản, giao dịch ghi nợ, khóa trùng và webhook đồng thời.

Kết quả ở môi trường test chỉ xác minh hành vi với dữ liệu kiểm soát. Kiểm thử MySQL thật, restart, kết nối SePay live và ghi có thật cần được chạy ở môi trường được duyệt.

## Kết quả ngày 22/11/2026

- Order Service `mvn verify`: build thành công, 71 test đạt, 2 test Testcontainers bỏ qua do môi trường thiếu Docker.
- Kiểm thử SePay qua MockMvc/H2: 6 test đạt, gồm chữ ký sai, timestamp cũ và body bị sửa.
- Kiosk Web `npm run build`: thành công.
- VietQR được xác minh theo URL và tham số sinh bởi backend. Chưa quét bằng ứng dụng ngân hàng.
- Webhook công khai, MySQL triển khai, khởi động lại, thanh toán thật và máy xịt thật: chưa kiểm chứng.

## Cập nhật đối soát API v2

- Đã thêm job đối soát giao dịch tiền vào trong cửa sổ 24 giờ gần nhất, mặc định tắt và chỉ chạy khi bật cấu hình cùng API token và UUID tài khoản MB.
- Đã mở rộng khóa giao dịch audit từ `BIGINT` sang UUID dạng chuỗi; database cũ cần backup và chạy migration có hướng dẫn thủ công trước khi triển khai.
- Bộ test mới cho phân trang và lọc tiền vào/đúng tài khoản đã chạy trong GitHub Actions và PASS ở commit `17df5cbd4a93c068e65dcd9101cb10cc4e9ad40d`, workflow [44986845744](https://github.com/phuoc523/reportDevelopingOOPsoftware/actions/runs/44986845744). Cả 5 job đều thành công. Maven Central không phân giải DNS nên workspace này không chạy lặp lại được.
