# Kiosk Web UI and QR Payment

## 1. Tổng quan và phạm vi

Kiosk Web là ứng dụng React 19 + TypeScript + Vite. Giao diện hiện dùng dữ liệu API thật; danh sách sản phẩm không tạo dữ liệu thay thế. Tài liệu này mô tả cải tiến product card, bước xác nhận lượt trải nghiệm, trang QR và placeholder mua chai.

Phạm vi chỉnh sửa trong đợt này là trình bày và responsive. Không đổi API, payload thanh toán, trạng thái nghiệp vụ hay logic tạo đơn/polling. QR vẫn được dựng từ `qrPayload` backend trả về bằng `qrcode.react`.

## 2. Cấu trúc và file liên quan

- `frontend/kiosk-web/src/App.tsx`: các màn catalog, xác nhận lượt thử, QR, dispensing, kết quả và placeholder mua chai; gọi API và quản lý trạng thái màn hình.
- `frontend/kiosk-web/src/App.css`: design tokens, product cards, flow cards, QR layout và responsive rules.
- `frontend/kiosk-web/src/index.css`: font mặc định và reset toàn cục; chiều rộng tối thiểu của trang là 320px.
- `frontend/kiosk-web/src/services/api.ts`: các HTTP request, token đăng nhập và chuyển lỗi API thành thông báo.
- `frontend/kiosk-web/src/types/api.ts`: kiểu dữ liệu cho Product, Order và Payment.
- `frontend/kiosk-web/README.md`: cách chạy Kiosk, tài khoản demo và giới hạn nghiệp vụ.

Ứng dụng dùng `DM Sans` cho nội dung và `Playfair Display` cho tiêu đề, theo cấu hình font hiện có; không thêm thư viện UI hoặc font mới.

## 3. Danh sách sản phẩm và luồng xác nhận

Sau đăng nhập, Kiosk tải máy, slots, sản phẩm và giá hiệu lực từ API. Sản phẩm được ghép với slot qua `productId`; mỗi thẻ dùng dữ liệu backend về tên, ảnh, ghi chú/mô tả, giá, khuyến mại, dung tích và khả dụng.

1. Nút **Thử mùi** mở bước xác nhận lượt trải nghiệm.
2. Bước xác nhận hiển thị tên, hình ảnh, mã slot, mô tả, liều đã hiệu chuẩn, máy và phí trải nghiệm.
3. **Thanh toán lượt thử** tạo Order, sau đó yêu cầu Payment QR.
4. Nút **Mua chai** vẫn dẫn đến giao diện thông báo giới hạn backend; giao diện không tạo order hoặc thanh toán giả cho việc mua chai.

## 4. Căn chỉnh product card

Product grid kéo các thẻ trong cùng một hàng về cùng chiều cao. Mỗi thẻ dùng cột Flexbox; nội dung mô tả và trạng thái giữ chiều cao tự nhiên, có thể xuống dòng, không bị cắt. Phần ưu đãi/trạng thái nằm trước footer, còn footer được đẩy xuống cuối vùng nội dung bằng `margin-top:auto`. Do đó đường viền trên footer—đường phân cách giá và nút—nằm ngang cùng hàng trong một hàng grid, kể cả khi mô tả hoặc thông báo trạng thái khác nhau.

Ảnh dùng tỉ lệ khung hình thay vì chiều cao cứng. Trên mobile, grid chuyển về một cột và card dùng khoảng đệm nhỏ hơn. Tên/mô tả dài được phép wrap; nội dung không bị line-clamp hoặc thay bằng dữ liệu rút gọn.

## 5. Typography và ngôn ngữ thiết kế

Giữ palette xanh đậm/kem/vàng, nền giấy, border và bo góc đang dùng. Các thay đổi tập trung vào độ đọc:

- Mã slot nhỏ hơn và có tracking; tên sản phẩm dùng Playfair Display, cỡ responsive và wrap khi dài.
- Mô tả dùng màu muted có tương phản tốt hơn, cỡ 15px và line-height 1.65.
- Giá chính nổi bật; giá gốc/giá khuyến mại dùng màu và cỡ chữ phụ.
- Nhãn trạng thái, dung tích, cảnh báo, focus ring và nút dùng kích thước/độ đậm nhất quán.
- Màn xác nhận và QR dùng cùng flow card, nền, border, typography và summary sản phẩm có ảnh.

## 6. Luồng QR payment hiện có

1. Kiosk gọi `POST /api/v1/orders` để tạo order.
2. Kiosk gọi `POST /api/v1/orders/{orderId}/payments/qr`.
3. Kiosk đưa nguyên `payment.qrPayload` vào `QRCodeSVG`; QR không được tạo từ số tiền hoặc reference tự dựng ở frontend.
4. Kiosk hiển thị số tiền, order ID, reference và hạn giao dịch từ backend.
5. Khi Payment là `PENDING`, Kiosk gọi `GET /api/v1/payments/{reference}` mỗi 3 giây.
6. `PAID` đưa Kiosk sang màn dispensing; `FAILED` và `EXPIRED` đưa sang kết quả tương ứng. Lỗi polling được hiển thị trên trang QR.

Backend hiện có các API callback/simulation riêng, nhưng Kiosk không gọi `/simulate` và không tự đánh dấu thanh toán thành công. Việc xác nhận và chuyển trạng thái thuộc backend.

## 7. API và dữ liệu được dùng

Các request của Kiosk đi qua `kioskApi` trong `src/services/api.ts`:

- `POST /api/auth/login`, `GET /api/auth/me`: đăng nhập và lấy principal.
- `GET /api/machines/{machineId}`, `GET /api/machines/{machineId}/slots`: máy và slots.
- `GET /api/products?tenantId=...`, `GET /api/pricing/products/{productId}?tenantId=...`: catalog và giá hiệu lực.
- `POST /api/v1/orders`: gửi `customerId`, `tenantId`, `machineId`, `slotId` và danh sách item gồm `productId`, `productName`, `unitPrice`, `quantity`.
- `POST /api/v1/orders/{orderId}/payments/qr`: nhận `PaymentResponse` gồm `reference`, `orderId`, `amount`, `currency`, `status`, `qrPayload`, `createdAt`, `expiresAt`, `paidAt`.
- `GET /api/v1/payments/{reference}`: đọc lại Payment trong quá trình polling.
- `GET /api/v1/orders/{orderId}/dispensing`: kết quả lệnh xịt sau khi thanh toán.

Giá, trạng thái, ID và URL QR không hardcode trong giao diện; chúng đến từ API. Trường `order.items`/`totalAmount` được dùng cho thông tin giao dịch khi có trong response.

## 8. Trạng thái và lỗi

- `PENDING`: hiển thị QR, trạng thái chờ và thời hạn; bắt đầu polling.
- `PAID`: chuyển sang luồng dispensing hiện có.
- `FAILED` hoặc `EXPIRED`: hiển thị kết quả thất bại/hết hạn; Kiosk không coi đây là thanh toán thành công.
- API lỗi hoặc mất kết nối: hiển thị thông báo lỗi. Màn QR có nút quay lại xác nhận; việc rời màn QR dừng polling. Nếu payment còn `PENDING` và cùng slot, Kiosk có thể tiếp tục giao dịch đang giữ thay vì tạo thêm order.
- Sản phẩm/slot/máy không khả dụng: nút **Thử mùi** bị khóa, thẻ hiển thị nguyên nhân tương ứng.

## 9. Giới hạn backend

- Backend hỗ trợ order cho lượt trải nghiệm và luồng payment mô phỏng; đây không phải tích hợp cổng thanh toán/ngân hàng thật.
- Nghiệp vụ bán chai chưa có DTO/API cho dung tích, giá bán lẻ, địa chỉ nhận hàng và fulfillment. Giao diện giữ thông báo giới hạn và không tạo giao dịch giả.
- Thanh toán thành công kích hoạt flow dispensing hiện có; không dùng order lượt thử để thay cho order mua chai.

## 10. Chạy và kiểm thử

Trong thư mục `frontend/kiosk-web`:

- `npm run dev`: chạy Vite dev server (README hiện cấu hình truy cập tại `http://127.0.0.1:7515` với proxy Gateway).
- `npm run lint`: kiểm tra ESLint.
- `npm run build`: chạy TypeScript build và Vite production build.

Kiểm thử thủ công sau khi có backend và dữ liệu demo:

1. Đăng nhập bằng tài khoản demo được ghi trong `frontend/kiosk-web/README.md`.
2. Kiểm tra catalog, máy và số slot khả dụng.
3. So sánh đường phân cách giá/nút giữa các card cùng hàng; thử card có tên/mô tả dài và card không khả dụng.
4. Chọn **Thử mùi**, kiểm tra phần xác nhận, rồi nhấn **Thanh toán lượt thử**.
5. Kiểm tra QR, amount, order ID, reference và expiry lấy từ API. Có thể dùng camera thiết bị để mở URL nếu backend trả về URL truy cập được trên thiết bị đó.
6. Kiểm tra trạng thái `PENDING`, sau đó callback/backend có thể xác nhận thanh toán để Kiosk chuyển sang dispensing; kiểm tra thất bại/hết hạn nếu có payment ở các trạng thái này.
7. Kiểm tra breakpoint desktop, tablet và mobile; xác nhận QR nằm trọn trong card và nội dung không tràn.

Kết quả kiểm tra tự động đã chạy trong đợt chỉnh sửa:

- `npm run lint`: thành công, ESLint không báo lỗi.
- `npm run build`: thành công, TypeScript build và Vite production build hoàn tất.

Chưa xác nhận bằng thiết bị điện thoại hoặc kiểm thử trực quan desktop/tablet/mobile trong trình duyệt ở đợt này.

## 11. File đã thay đổi

- `frontend/kiosk-web/src/App.tsx`: bố cục summary sản phẩm nhất quán ở xác nhận, QR và placeholder mua chai; thông tin payment có order ID và status; giữ nguyên API và nghiệp vụ.
- `frontend/kiosk-web/src/App.css`: căn footer card đồng hàng, typography dễ đọc hơn, flow-card/QR responsive và style trạng thái.
- `frontend/kiosk-web/src/index.css`: giảm min-width từ 360px xuống 320px để hỗ trợ viewport hẹp hơn.
- `frontend/kiosk-web/README.md`: sửa mô tả QR cũ, ghi QR payload được backend trả về và Kiosk polling mỗi 3 giây.
- `docs/kiosk-web/kiosk-web-ui-and-qr-payment.md`: tài liệu triển khai và kiểm thử giao diện/QR.

## 12. Vấn đề còn lại

- Tài khoản, catalog, trạng thái máy và slot phải được seed/cấu hình đúng để tạo order.
- Điện thoại chỉ mở được QR nếu URL trong `qrPayload` có thể truy cập từ mạng của điện thoại; `localhost` trong QR không trỏ đến máy chạy Kiosk.
- Cần chạy kiểm thử trực quan trên thiết bị thật và các viewport mục tiêu để xác nhận hành vi rendering ngoài kết quả build/lint.
