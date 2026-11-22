# Cấu hình webhook SePay

1. Dùng endpoint HTTPS đã được phê duyệt trỏ tới API Gateway, đường dẫn `/api/v1/webhooks/sepay`. Chỉ mở đường dẫn cần thiết cho callback.
2. Trên SePay, tạo webhook sự kiện tiền vào, chọn tài khoản MB đúng và định dạng JSON. Chọn xác thực **HMAC-SHA256**, cùng giá trị với `SEPAY_WEBHOOK_SECRET` ở Order Service. Gateway phải giữ nguyên body và hai header `X-SePay-Timestamp`, `X-SePay-Signature`.
3. Nếu bật nhận diện mã thanh toán, cấu hình tiền tố `SS` và hậu tố 20 ký tự chữ hoặc số. Backend yêu cầu nội dung có mã chính xác. Nếu SePay không trả `code`, backend chỉ chấp nhận nội dung bằng chính mã.
4. Dùng tài khoản và khóa riêng cho Test Mode. Kiểm tra QR, nội dung chuyển khoản, webhook log và trạng thái đơn trên môi trường test. Dùng Machine Service mô phỏng hoặc cô lập máy thật.
5. Khi bảo mật, schema và HTTPS đã được xác minh, người dùng có thể tự chuyển khoản thử sau khi phê duyệt. Đối chiếu lịch sử MB, lịch sử SePay, bảng sự kiện, trạng thái đơn và một lệnh xịt đã lưu.

SePay cần HTTP 200 cùng `{"success":true}`. Order Service chỉ trả kết quả đó sau khi ghi bền vững hoặc xác nhận giao dịch đã ghi trước đó. Các giao dịch đã nhận nhưng không khớp được lưu với lý do. Dùng lịch sử giao dịch SePay và bảng sự kiện để tìm callback bị thiếu. Nếu giao dịch còn trong hạn và đủ điều kiện, dùng chức năng phát lại chính sự kiện phía SePay. Giao dịch quá hạn cần đối soát thủ công.

- [Hướng dẫn tích hợp Webhook của SePay](https://docs.sepay.vn/tich-hop-webhooks.html)
- [Mô phỏng và phát lại giao dịch](https://developer.sepay.vn/vi/tien-ich-khac/test-mode/mo-phong-giao-dich)
