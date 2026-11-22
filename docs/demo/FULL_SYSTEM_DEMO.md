# Kịch bản demo ScentStation

Xem [cách khởi động](HOME_ONE_CLICK_SETUP.md). Launcher khởi động MySQL, Redis và Kafka, rồi dùng dependency graph của Compose để khởi động backend và frontend. Trình kiểm tra dùng container health và HTTP trên các cổng loopback.

| Ứng dụng | URL |
|---|---|
| Admin | http://127.0.0.1:7514 |
| Kiosk | http://127.0.0.1:7515 |
| Mock Payment | http://127.0.0.1:3000 |
| Gateway | http://127.0.0.1:8080/actuator/health |

Sau khi các health check đạt, dùng tài khoản thử của tenant demo đã được cấp qua quy trình Identity hiện có. Kiểm tra danh sách sản phẩm và máy demo. Tạo đơn thử từ Kiosk và quét QR sandbox bằng trình duyệt trên cùng máy. Xác nhận mô phỏng, xem trạng thái đơn và đọc kết quả trong Admin hoặc Reporting. Kiểm tra tenant khác không đọc được đơn và gửi lại sự kiện sandbox để kiểm tra idempotency.

Chỉ sử dụng database và máy logic thuộc project demo. Token mô phỏng phục vụ giao diện sandbox. Xác nhận từ ngân hàng cần luồng provider và đối soát riêng.
