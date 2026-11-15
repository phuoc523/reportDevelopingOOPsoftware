# ScentStation Kiosk Web

Kiosk Web dùng API thật qua API Gateway. Không có dữ liệu sản phẩm, đơn hàng,
thanh toán hoặc kết quả xịt giả lập trong giao diện.

## Chạy local

Từ thư mục gốc repository:

```powershell
docker compose up -d
powershell -ExecutionPolicy Bypass -File .\infra\demo-data\seed-demo.ps1
cd frontend\kiosk-web
npm.cmd install
npm.cmd run dev
```

Mở `http://127.0.0.1:7515`. Vite proxy `/api` đến Gateway tại
`http://127.0.0.1:8080`.

Tài khoản local cho máy `9101`:

- Tên đăng nhập: `kiosk.customer`
- Mật khẩu: `Demo@2026!`

Không dùng tài khoản hoặc mật khẩu demo trong staging/production.

## Cấu hình

Sao chép `.env.example` thành `.env.local` nếu cần đổi máy hoặc Gateway:

```dotenv
VITE_API_BASE_URL=
VITE_KIOSK_MACHINE_ID=9101
```

Khi để trống `VITE_API_BASE_URL`, trình duyệt gọi cùng origin và Vite proxy xử lý
trong môi trường phát triển. Với bản build tĩnh, đặt biến này thành URL Gateway
được phép CORS hoặc cấu hình reverse proxy cùng origin.

## Luồng API

1. Identity: đăng nhập và lấy principal/JWT.
2. Machine + Catalog: tải máy, slot, sản phẩm và giá khuyến mại hiệu lực.
3. Order: tạo đơn `PENDING`; backend kiểm tra tenant, khách hàng, giá, máy và slot.
4. Payment: tạo/lấy QR và thăm dò trạng thái theo reference.
5. Dispensing: sau callback `PAID`, Order Service tự gửi lệnh xịt; Kiosk chỉ đọc kết quả.

Kiosk cố ý không gọi endpoint `/simulate` và không gọi trực tiếp endpoint nội bộ
tạo dispense command.

Mỗi lượt thử dùng đúng liều đã hiệu chuẩn trong slot (`doseMl`, dữ liệu demo hiện tại là
`0.12 ml`). Khách có thể chọn **Mua chai** sau khi thử, nhưng giao diện chỉ hiển thị trạng
thái chờ tích hợp và không tạo đơn giả khi backend chưa có nghiệp vụ bán chai.

## Giới hạn backend hiện tại

- Payload QR đang là URI `scentstation://...`, chưa phải tích hợp cổng thanh toán thật.
- Chưa có endpoint browse chuyên biệt dùng quyền `KIOSK_BROWSE`; tài khoản demo cần
  thêm quyền đọc Catalog/Machine với scope máy `9101`.
- Đăng ký công khai tạo `CUSTOMER` không có tenant, nên tài khoản đó chưa thể tạo đơn.
- Tạo Order chưa nhận idempotency key; UI khóa thao tác lặp nhưng backend chưa bảo vệ
  hoàn toàn trước retry mạng tạo hai đơn khác nhau.
- Backend chưa có loại đơn mua chai, dung tích bán lẻ, địa chỉ nhận hàng hoặc luồng
  fulfillment. Không dùng Order xịt hiện tại để thay thế vì thanh toán thành công sẽ kích
  hoạt lệnh xịt tại kiosk.
