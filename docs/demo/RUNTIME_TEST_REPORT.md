# Báo cáo runtime V10

Windows 10 Pro build 19045. CPU báo virtualization firmware và SLAT khả dụng, nhưng hypervisor chưa chạy và WSL không cung cấp phiên bản đang hoạt động. RAM tổng khoảng 31,8 GiB, còn 17,9 GiB khả dụng khi preflight. Ổ C còn khoảng 94,6 GiB. Java 21, Maven 3.9.11 và Node 22 portable đã có.

Ba frontend hiện có trả HTTP 200 tại 7514, 7515 và 3000. Gateway 8080 không kết nối được. Docker Engine và kiểm thử runtime Compose bị chặn trên máy này. Đăng nhập qua trình duyệt, kết nối Catalog, tạo đơn và xác nhận sandbox qua frontend cần một lần chạy trên Engine đủ điều kiện.

- 61 test hồi quy Order Service và Payment Sandbox chạy với H2 và dịch vụ giả lập. Tất cả đạt.
- Admin, Kiosk và Mock Payment build thành công bằng Node 22 từ bản sao tạm.
- PowerShell parse thành công. Kiểm thử tạo khóa, giữ nguyên cấu hình, chạy lại và phát hiện thiếu Docker đều đạt.
- Compose 2.39.4 xác thực được cấu hình, mạng nội bộ, cổng loopback, cấu hình Gateway và cặp khóa MySQL mà không cần chạy Engine.

Chỉ xác nhận demo đầy đủ sau khi health checks và các luồng nghiệp vụ trong [kịch bản demo](FULL_SYSTEM_DEMO.md) đều đạt.
