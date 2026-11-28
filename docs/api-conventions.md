# Quy ước API Identity và Platform

Quy ước này cập nhật đề xuất từ nhánh `feature/OOP-15-shared-api-conventions` theo các endpoint có trên baseline hiện hành.

## Định tuyến và hợp đồng

- Dùng Gateway cho đường dẫn công khai `/api/auth/**`, `/api/admin/**`, `/api/staff/**`, `/api/customer/**`, `/api/platform/**`, `/api/brand/**` và `/api/audit/**` của Identity.
- Dùng GET để đọc. Dùng POST để tạo tài nguyên hoặc thực hiện hành động như đăng nhập và phê duyệt. Giữ phương thức và đường dẫn hiện hành khi chỉnh implementation.
- Gửi nội dung JSON với `Content-Type: application/json`. Client gửi `Authorization: Bearer <token>` cho các API yêu cầu xác thực.
- API nội bộ dùng `/internal/**` và khóa liên dịch vụ khi được yêu cầu. Client gọi API công khai qua Gateway.
- Gateway phát sinh `X-Request-Id` tại ranh giới công khai để dịch vụ phía sau và người gọi truy vết yêu cầu.

## Phản hồi

| Mã | Ý nghĩa |
|---|---|
| 200, 201, 204 | Thành công, tạo tài nguyên, thành công với body rỗng. |
| 400, 409 | Request không hợp lệ hoặc xung đột với trạng thái hiện tại. |
| 401, 403 | Thiếu phiên hợp lệ hoặc quyền không đủ. |
| 404 | Tài nguyên hoặc đường dẫn không tồn tại. |
| 429 | Gateway đã đạt giới hạn. Dùng giá trị giây của `Retry-After`. |
| 503 | Gateway chưa thể xác minh danh tính dùng để giới hạn hoặc chưa thể sử dụng bộ đếm Redis. |

Identity trả về `timestamp`, `status` và `error` cho lỗi xử lý bằng controller advice. Lỗi tại filter bảo mật và phản hồi do Gateway tạo có thể chỉ có HTTP status. Client nên xử lý status trước và đọc body nếu có.
