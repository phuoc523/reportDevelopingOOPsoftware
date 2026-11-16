# Kiosk Web: Bottle Purchase and QR Checkout

## 1. Tổng quan

Kiosk Web là ứng dụng React + TypeScript + Vite cho khách hàng. Tài liệu này ghi nhận trạng thái thực tế của luồng danh sách sản phẩm, trải nghiệm xịt, giao diện thông tin mua chai và QR payment hiện có.

**Mua chai chưa thể checkout.** Kiểm tra API/model hiện tại không tìm thấy dữ liệu biến thể dung tích, giá bán lẻ chai, tồn kho chai, vận chuyển/nhận hàng hoặc loại order bán chai. Vì vậy Kiosk chỉ hiển thị thông tin sản phẩm có thật và lý do chưa thể mua; không tạo đơn/QR giả và không dùng phí thử mùi để làm giá chai.

## 2. Phạm vi đã chỉnh sửa

- Hoàn thiện nội dung màn `purchase` hiện có bằng chi tiết sản phẩm và trạng thái rõ ràng về các capability backend còn thiếu.
- Nút xác nhận mua chai vẫn bị vô hiệu hóa; thao tác quay lại giữ hành vi hiện tại.
- Đổi link chữ đăng xuất thành nút có border, icon, hover, pressed và disabled style; giữ nguyên logout API và thao tác xóa token/state.
- Không sửa API client, API contract, QR flow, backend, service khác hoặc database.

## 3. Cấu trúc và file quan trọng

- `frontend/kiosk-web/src/App.tsx`: màn hình, state đăng nhập/chọn sản phẩm/order/payment, giao diện catalog, purchase, QR và logout.
- `frontend/kiosk-web/src/App.css`: style Kiosk, responsive layout, purchase capability panel và logout button.
- `frontend/kiosk-web/src/index.css`: font/reset toàn cục.
- `frontend/kiosk-web/src/services/api.ts`: API client và token session.
- `frontend/kiosk-web/src/types/api.ts`: type Product, OrderResponse, PaymentResponse và các type liên quan.
- `frontend/kiosk-web/README.md`: hướng dẫn chạy, tài khoản local và giới hạn nghiệp vụ.
- `docs/kiosk-web/bottle-purchase-and-qr-checkout.md`: tài liệu trạng thái luồng này.

## 4. Luồng hiện có từ danh sách sản phẩm

- Kiosk tải Machine, Slots, Product và giá thông qua API; sản phẩm hiển thị được ghép theo `slot.productId`.
- **Thử mùi** mở bước xác nhận và dùng `slot.doseMl`/giá trải nghiệm đang có.
- **Mua chai** giữ đối tượng Product được chọn trong React state và mở màn `purchase`. Không mất lựa chọn khi chuyển màn.
- Màn purchase hiện hiển thị ảnh/tên/mô tả và **ID sản phẩm** trả về từ API. Không suy diễn ID này thành SKU.
- Bốn capability chưa có được thông báo: dung tích/đơn giá chai, tồn kho/số lượng, nhận/giao hàng, và loại order bán chai.
- Kiosk không hiển thị `defaultPrice`/`effectivePrice` như giá bán chai vì trong luồng này đó là giá được dùng cho trải nghiệm.
- Không thể xác nhận mua chai; người dùng có thể quay lại catalog hoặc màn kết quả theo nơi đã mở purchase.

## 5. Vì sao chưa thể tạo checkout chai

`Product` trong `frontend/kiosk-web/src/types/api.ts` chỉ có `id`, `tenantId`, `name`, `description`, `fragranceNotes`, `imageUrl`, `defaultPrice`, `available`. Không có variant/dung tích chai, retail price, stock quantity hay delivery information.

Order hiện có yêu cầu `customerId`, `tenantId`, `machineId`, `slotId` và các `items`. Trong `OrderService.createOrder`, backend xác nhận machine/slot khả dụng, xác nhận sản phẩm khớp slot, kiểm tra sản phẩm/giá với Catalog. Payment `PAID` trong Order Service gọi `requestDispensing` cho order. Do đó dùng endpoint Order/QR hiện tại cho đơn mua chai có nguy cơ tạo lệnh xịt; Kiosk không làm vậy.

Vì không có dữ liệu authoritative cho bán chai, UI không tạo control dung tích, quantity, shipping, đơn giá/tổng tiền hoặc nút checkout có hiệu lực. Việc thêm chúng sẽ tạo cảm giác giao dịch hoạt động nhưng không có cơ sở backend.

## 6. QR payment hiện có trong Kiosk

QR này thuộc flow **thanh toán lượt trải nghiệm**, không phải bottle checkout. UI hiện tại được giữ nguyên trong task này.

1. Kiosk gọi tạo order trải nghiệm.
2. Sau khi backend trả order, Kiosk gọi tạo/lấy QR payment theo order ID.
3. `QRCodeSVG` render nguyên chuỗi `payment.qrPayload` do backend trả về.
4. Kiosk hiển thị amount, currency, payment reference, order ID và expiry từ response.
5. Khi status `PENDING`, Kiosk gọi API payment status mỗi 3 giây.
6. `PAID` chuyển sang màn dispensing; `FAILED`/`EXPIRED` chuyển sang kết quả lỗi tương ứng. Kiosk không tự đặt status thành công và không gọi endpoint `/simulate`.

Không có ngân hàng/provider hoặc thông tin chuyển khoản bên ngoài trong response/type của Kiosk. Không hiển thị tên ngân hàng giả.

## 7. API hiện đang được sử dụng

Các request được khai báo trong `frontend/kiosk-web/src/services/api.ts`; mặc định Kiosk gọi cùng origin qua Vite proxy/API Gateway.

| Method và route | Mục đích |
|---|---|
| `POST /api/auth/login` | Đăng nhập và nhận token |
| `GET /api/auth/me` | Lấy principal hiện tại |
| `POST /api/auth/logout` | Logout hiện có |
| `GET /api/machines/{machineId}` | Đọc trạng thái máy |
| `GET /api/machines/{machineId}/slots` | Đọc slot và khả dụng |
| `GET /api/products?tenantId={tenantId}` | Lấy Product data hiện có |
| `GET /api/pricing/products/{productId}?tenantId={tenantId}` | Lấy giá hiệu lực đang dùng cho trải nghiệm |
| `POST /api/v1/orders` | Tạo order Kiosk với customer/tenant/machine/slot/items |
| `POST /api/v1/orders/{orderId}/payments/qr` | Tạo hoặc lấy QR payment của order hiện có |
| `GET /api/v1/payments/{reference}` | Đọc trạng thái payment để polling |
| `GET /api/v1/orders/{orderId}/dispensing` | Đọc kết quả dispensing |

Không tìm thấy API bottle variants/stock/delivery/bottle order trong hợp đồng Kiosk đã kiểm tra. Không gọi các route này từ UI.

Request tạo Order hiện tại gồm `customerId` (number), `tenantId` (string), `machineId`/`slotId` (string), và `items[]` với `productId` (number), `productName` (string), `unitPrice` (number), `quantity` (number). Kiosk gửi một item cho lượt trải nghiệm đã chọn. Đây không phải request mua chai. Giá order được backend kiểm tra với Catalog; UI không sử dụng dữ liệu này để tuyên bố giá chai.

`PaymentResponse` gồm `reference`, `orderId`, `amount`, `currency`, `status`, `qrPayload`, `createdAt`, `expiresAt`, `paidAt`. Trạng thái frontend khai báo: `PENDING`, `PAID`, `FAILED`, `EXPIRED`.

## 8. State, xác thực và lỗi

- Màn hình (`catalog`, `confirm`, `purchase`, `payment`...) và Product đang chọn được quản lý trong React state; chuyển màn không làm mất selected Product.
- JWT được lưu trong `sessionStorage` thông qua `authToken`; logout giữ flow gọi API rồi xóa token và reset state như trước.
- Kiosk kiểm tra grant `KIOSK_ORDER`, tenant, machine/slot và product availability trước khi cho tạo order trải nghiệm.
- API error được chuyển thành thông báo bởi `ApiError`; lỗi tạo order/payment hiển thị trong Kiosk hiện có.
- Không có trạng thái payment cho bottle checkout vì backend chưa có loại order này.

## 9. Cách chạy và kiểm thử

Theo `frontend/kiosk-web/README.md`:

1. Khởi động backend/API Gateway và seed dữ liệu local theo hướng dẫn repo.
2. Chạy Kiosk bằng `npm run dev` trong `frontend/kiosk-web`; README cấu hình `http://127.0.0.1:7515`.
3. Đăng nhập tài khoản demo local được ghi trong README.
4. Mở catalog, chọn **Mua chai**; kiểm tra Product ID/tên/ảnh/mô tả và thông báo các trường còn thiếu.
5. Kiểm tra nút xác nhận bị disable và **Quay lại** trả về màn trước.
6. Chọn **Thử mùi** để kiểm tra luồng hiện hữu riêng; chỉ tiếp tục nếu hiểu rằng thanh toán thành công sẽ kích hoạt dispensing.
7. Chạy `npm run lint` và `npm run build` trong Kiosk Web.

Kết quả kiểm tra code trong lần cập nhật này:

- `npm run lint`: thành công.
- `npm run build`: thành công, TypeScript và Vite build hoàn tất.
- Không thực hiện giao dịch thực, không kiểm thử backend bottle purchase (API tương ứng chưa có), và không xác nhận giao diện trên thiết bị thật trong lần này.

## 10. Trạng thái hoàn thành và phụ thuộc tiếp theo

**Đã làm trong Kiosk**

- Trang mua chai hiển thị thông tin Product thật và ID Product.
- Các trường/luồng chưa được backend hỗ trợ được nêu minh bạch.
- Không phát sinh giá/dung tích/tồn kho/địa chỉ giả hoặc order/QR giả.
- Logout được trình bày như button và giữ nguyên handler.
- Luồng xịt thử/QR hiện có không bị đổi API hoặc nghiệp vụ trong task này.

**Chưa hoàn thành, cần Backend trước khi triển khai checkout chai**

- Product variants/volume, retail price, stock và rules quantity.
- Phương thức nhận hàng, địa điểm/địa chỉ, shipping fee/fulfillment nếu cần.
- Loại order chai và validation authoritative về giá/tồn kho.
- Payment flow gắn với loại order chai, không kích hoạt dispensing.
- Response/order status cần cho receipt, kết quả và hướng dẫn nhận hàng.

Sau khi các API/DTO/authorization rules này tồn tại, Kiosk mới có thể thêm chọn dung tích/số lượng, xác nhận tổng tiền từ backend, QR checkout và trang kết quả cho đơn chai. Không tái sử dụng order trải nghiệm trừ khi backend định nghĩa rõ loại order và ngăn side effect dispensing.
