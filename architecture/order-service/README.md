# PHÂN HỆ ORDER & PAYMENT SERVICE (:8084)

## 1. Giới thiệu phân hệ
`order-service` là trái tim điều phối giao dịch mua bán và thanh toán của toàn bộ hệ thống:
- Sở hữu cơ sở dữ liệu `order_db`.
- Đảm nhận toàn bộ vòng đời đơn hàng (`Order`, `OrderItem`), quản lý chuyển đổi trạng thái hợp lệ (`PENDING` $\rightarrow$ `CONFIRMED` $\rightarrow$ `DISPENSED`, v.v.).
- Chịu trách nhiệm bảo đảm tính thẩm quyền (*Authoritative Pricing*): Kiểm tra chéo với `machine-service` và `catalog-service` khi tạo đơn để chống gian lận giá.
- Xử lý Webhook thanh toán chống trùng lặp (*Idempotency*) thông qua `PaymentCallbackEvent`.
- Điều phối lệnh xịt nước hoa an toàn duy nhất một lần (*At-most-once Dispensing*) qua `DispensingCommand` và tự động mở hồ sơ rà soát sự cố (`TransactionReview`) khi phần cứng gặp trục trặc.

## 2. Danh mục sơ đồ chi tiết
1. **`use-case.puml`**:
   - Thể hiện các ca sử dụng tạo đơn, sinh mã QR, tiếp nhận webhook thanh toán, điều phối lệnh phun, mở hồ sơ sự cố và đồng bộ báo cáo.
   - Quan hệ `<<include>>` (Callback thanh toán bắt buộc phải xác nhận đơn và kích hoạt phun) và `<<extend>>` (Mở review khi lệnh phun thất bại).
2. **`class-diagram.puml`**:
   - Thực thể chính: `Order`, `OrderItem`, `Payment`, `PaymentCallbackEvent`, `DispensingCommand`, `TransactionReview`.
   - Quan hệ: `Order` *-- `OrderItem` (Composition), `Order` o-- `Payment` (Aggregation), `Order` o-- `DispensingCommand` (Aggregation), `Payment` *-- `PaymentCallbackEvent` (Composition).
   - Tầng điều khiển & dịch vụ: `OrderController`, `PaymentController`, `TransactionReviewController`, `OrderService`, `PaymentService`, `DispensingService`, `TransactionReviewService`.
3. **`sequence-diagram.puml`**:
   - Thể hiện luồng xử lý Callback Webhook hoàn chỉnh: Xác thực Token $\rightarrow$ Chống trùng lặp qua eventId $\rightarrow$ Cập nhật Payment PAID và Order CONFIRMED $\rightarrow$ Gửi lệnh sang Machine Service $\rightarrow$ Nhánh rẽ thành công (DISPENSED) và nhánh ngoại lệ (Mở TransactionReview).
4. **`activity-diagram.puml`**:
   - Biểu đồ phân làn thể hiện toàn bộ vòng đời từ khi khách hàng chọn hương tại Kiosk, quét QR thanh toán, đến khi máy xịt nước hoa hoặc xử lý tình huống lỗi kỹ thuật.
