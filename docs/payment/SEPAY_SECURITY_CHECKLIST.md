# Kiểm tra an toàn SePay

- Dùng HTTPS, secret HMAC riêng cho từng môi trường, giữ khóa ngoài Git và log. Chỉ bản triển khai live được dùng khóa live.
- Kiểm tra đúng MB, tài khoản ghi có, mã và độ dài mã, số tiền VND, thời điểm giao dịch, trạng thái và thời hạn đơn.
- Mã đơn tra cứu toàn cục chỉ dẫn tới một payment đã lưu. Các API kiosk vẫn xác thực quyền và tenant như trước.
- Giao dịch trùng bị chặn bằng khóa transaction ID và khóa tham chiếu ngân hàng theo tài khoản. Cập nhật vận hành dùng khóa hàng đơn. Ghi thanh toán và lệnh xịt trong cùng transaction.
- Audit lưu dữ liệu cần cho đối soát. Giới hạn quyền đọc bảng, thời hạn lưu giữ và quyền truy cập log webhook.
- Kiosk có thể gửi tham số số tài khoản, số tiền và mã đơn đến dịch vụ ảnh VietQR, do đó cần phê duyệt lưu lượng đến `https://vietqr.app` khi triển khai. Secret webhook không nằm trong URL.
- Trước khi mở live, xác minh payload MB thật, cơ chế phát lại, behavior của MySQL khi webhook đồng thời, kịch bản khởi động lại và trạng thái lệnh xịt khi máy mất kết nối.
