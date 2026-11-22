# Giới hạn hiện tại

- Docker Desktop, WSL 2 và Linux Engine là điều kiện để chạy backend. Các cổng 8080, 7514, 7515 và 3000 phải khả dụng hoặc đã do đúng project demo phục vụ.
- Cần có tài khoản, tenant, danh mục và máy demo hợp lệ để chạy smoke test nghiệp vụ. Launcher không thêm dữ liệu mẫu ngoài quy trình khởi tạo vốn có của các service.
- Monitoring nâng cao và Firebase nằm ngoài launcher demo. Backend và Redis dùng mạng nội bộ riêng. Gateway và frontend dùng mạng ingress riêng với cổng chỉ bind loopback.
- QR hiện mở Mock Payment Web trên cùng máy. Payment Live, VietQR ngân hàng và hóa đơn điện tử chưa được xác nhận.
- Giữ `.env.demo` để tiếp tục cùng bộ dữ liệu. Nếu file mất, project mới sẽ có volume riêng. Các volume cũ được giữ lại.
