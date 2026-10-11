# Identity Service và API Gateway

## 1. Phạm vi phụ trách

Người 1 phụ trách nền tảng xác thực và định tuyến API cho dự án ScentStation.

## 2. Identity Service

- Đăng ký và đăng nhập.
- Quản lý tài khoản người dùng.
- Phân quyền Admin, Staff và Customer.
- Xác thực người dùng và bảo vệ API.
- Xử lý lỗi liên quan đến xác thực và quyền truy cập.

## 3. API Gateway

- Tiếp nhận và định tuyến request đến các service.
- Cấu hình CORS cơ bản.
- Thống nhất cấu hình giao tiếp API với các thành viên.
- Phối hợp kiểm tra cơ chế xác thực và quyền truy cập.

## 4. Bàn giao

- Mã nguồn Identity Service.
- Mã nguồn API Gateway.
- Tài liệu về xác thực và phân quyền.
- Kiểm thử đăng nhập và quyền truy cập.

## 5. Giới hạn trách nhiệm

Không phụ trách Docker cho toàn hệ thống hoặc tích hợp toàn bộ ứng dụng. Các phần này được phối hợp với các thành viên khác khi cần.
