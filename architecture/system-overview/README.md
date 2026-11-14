# TỔNG QUAN KIẾN TRÚC HỆ THỐNG SCENTSTATION

Thư mục này chứa các sơ đồ toàn cảnh, làm nền tảng trước khi đi sâu vào chi tiết của từng microservice riêng lẻ:

1. **`system-architecture.puml`**:
   - Biểu diễn mô hình 4 tầng: Tầng Client/Actor $\rightarrow$ Tầng Edge (API Gateway :8080) $\rightarrow$ Tầng Dịch vụ Nghiệp vụ (6 Microservices) $\rightarrow$ Tầng Dữ liệu riêng biệt (Database-per-service).
   - Thể hiện rõ các kiểu đường nối:
     - Đường nét liền có mũi tên (`-->`): Lời gọi đồng bộ REST HTTP từ Client vào Gateway và từ Gateway chuyển tiếp đến các service.
     - Đường nét đứt có mũi tên (`..>`): Lời gọi đồng bộ nội bộ giữa các service (Inter-service REST call) được bảo vệ bằng `X-Internal-Service-Key`.
     - Đường nét liền không mũi tên (`--`): Quyền sở hữu dữ liệu độc quyền của từng Service đối với Database của nó.
     - Đường hiện thực (`..|>`): Giao tiếp phần cứng IoT Kiosk qua giao thức Serial/Relay Pulse.

2. **`system-use-case.puml`**:
   - Sơ đồ ca sử dụng toàn hệ thống phân định rõ ranh giới giữa Khách hàng Kiosk (`Customer`), Quản trị thương hiệu (`Brand Admin`), Quản lý vận hành (`Ops Manager`), Kỹ thuật viên (`Technician`), Quản trị hệ thống (`Super Admin`) và các Actor ngoài (`Payment Provider`, `IoT Sensor`).
   - Làm nổi bật quan hệ `<<include>>` (ràng buộc phải có) và `<<extend>>` (mở rộng khi có sự kiện bất thường hoặc lỗi phát sinh).

3. **`core-flow-sequence.puml`**:
   - Luồng nghiệp vụ cốt lõi xuyên suốt 4 giai đoạn: Tạo đơn hàng Authoritative $\rightarrow$ Sinh QR và thanh toán $\rightarrow$ Webhook callback và điều phối lệnh xịt nước hoa $\rightarrow$ Khách hàng nhận nước hoa tại trạm máy.
