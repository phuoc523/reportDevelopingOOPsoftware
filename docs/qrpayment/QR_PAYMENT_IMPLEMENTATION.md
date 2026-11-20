# QR Payment Implementation

## Luồng hiện tại

Kiosk hiển thị **một QR duy nhất cho mỗi đơn hàng**. QR chỉ chứa JSON riêng của
payment:
`{"type":"SCENTSTATION_PAYMENT","paymentReference":"PAY-..."}`.

URL chung `http://{LAN_HOST}:3000/` được hiển thị dưới dạng địa chỉ/link riêng,
không mã hóa thành QR. Khách mở URL trên điện thoại rồi nhấn **Quét mã QR** để
đọc QR giao dịch trên kiosk. Reference không xuất hiện trong URL, không được
hiển thị và không yêu cầu nhập bằng tay.

`LAN_HOST` được lấy từ IPv4 của Wireless LAN adapter đang hoạt động bằng
`scripts/Start-LanPayment.ps1`. Script cập nhật riêng `LAN_HOST` và
`PAYMENT_MOCK_WEB_BASE_URL` trong `.env`, không ghi đè các biến còn lại. Order
Service trả `qrUrl` riêng với `qrPayload`; Compose truyền URL gốc vào
`PAYMENT_MOCK_WEB_BASE_URL`. Port web được cố định ở `3000`.

## Luồng API và xác minh

- Kiosk tạo order và gọi `POST /api/v1/orders/{orderId}/payments/qr`.
- Order Service tạo hoặc trả lại payment hiện có, với reference riêng từng
  payment.
- Kiosk hiển thị `qrUrl` dưới dạng link/văn bản và chỉ render QR từ
  `qrPayload`.
- Mock Payment xác thực JSON (`type` và `paymentReference`), sau đó gửi
  `POST /api/v1/payments/public/lookup` với reference trong request body.
- Backend tra cứu payment độc lập theo reference và trả về đúng order, machine,
  slot, items, amount, currency, status và expiry; response không trả customer
  hoặc tenant identifiers.
- Khi khách chọn **Thanh toán**, web gửi callback body tới
  `POST /api/v1/payments/public/confirm`. Backend tiếp tục xác minh reference,
  order, amount, currency, expiry, trạng thái và idempotency; callback token
  chỉ được dùng phía backend.
- Luồng xác nhận order, outbox/event và lệnh dispensing hiện có không thay đổi.
- Kiosk tiếp tục polling trạng thái payment độc lập.

Mock Payment giữ payment/order trong React state riêng của từng tab. Backend
không dùng current order hoặc reference global. Nút **Quay lại** chỉ xóa state
cục bộ của tab; không hủy payment, tạo payment mới hoặc cập nhật trạng thái.

Frontend dùng các API POST body `POST /api/v1/payments/public/lookup` và
`POST /api/v1/payments/public/confirm`. Các endpoint GET/confirm public cũ có
reference trong path đã được bỏ sau khi xác nhận không còn consumer trong
repository. Callback provider hiện tại vẫn được giữ nguyên.

## Camera và giới hạn HTTP LAN

`getUserMedia()` chỉ khả dụng trong secure context. HTTP trên IPv4 LAN không
phải secure context, vì vậy trình duyệt hiện đại thường chặn camera trực tiếp.
Giao diện không chuyển sang giải pháp chụp ảnh thay thế; nó báo lỗi và không
tuyên bố quét được khi origin không an toàn. Chưa kiểm thử trên thiết bị Android
hoặc iOS thực tế. Xem
[MDN getUserMedia](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia).

Không tự chuyển URL sang HTTPS/tunnel. Nếu live camera là điều kiện bắt buộc,
cần cung cấp origin HTTPS đáng tin cậy; đây là thay đổi triển khai cần được
chấp thuận riêng.

## Chạy và kiểm thử

Trên Windows PowerShell, chạy:

```powershell
.\scripts\Start-LanPayment.ps1
```

Sau thay đổi source, build và tạo lại các service đã đổi:

```powershell
docker compose up -d --build order-service mock-payment-web
```

Các kiểm tra tự động hiện bao gồm:

- kiểm tra frontend parse đúng payload giao dịch, từ chối payload sai kiểu,
  thiếu reference hoặc chứa thêm URL;
- kiểm tra backend tạo URL gốc riêng, payload giao dịch có reference đúng,
  reference hai đơn khác nhau độc lập;
- kiểm tra lookup/confirm body gắn đúng reference với order tương ứng và
  confirm một payment không làm payment khác thay đổi;
- build Mock Payment Web và kiosk, cùng Order Service payment controller tests.

Các kiểm tra tự động không thay thế kiểm thử camera thực trên điện thoại.
