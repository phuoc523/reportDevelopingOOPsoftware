# Quy ước API dùng chung

## 1. Mục đích

Tài liệu này quy định các quy ước API thống nhất cho các dịch vụ trong hệ thống ScentStation.

Tất cả dịch vụ cần tuân thủ các quy tắc này nhằm bảo đảm API nhất quán, dễ sử dụng, dễ kiểm thử và dễ bảo trì.

## 2. Quy tắc đặt tên URL và tài nguyên

- Sử dụng tiền tố `/api/v1` cho các endpoint REST API công khai.
- Sử dụng danh từ để biểu diễn tài nguyên.
- Ưu tiên tên tài nguyên ở dạng số nhiều khi phù hợp.
- Sử dụng chữ thường và dấu gạch ngang trong đường dẫn URL.
- Hạn chế sử dụng động từ trong URL khi phương thức HTTP tiêu chuẩn đã thể hiện được hành động.
- Sử dụng `{id}` để biểu diễn định danh tài nguyên trong tài liệu API.

Ví dụ:

- `GET /api/v1/products`
- `GET /api/v1/products/{id}`
- `POST /api/v1/products`
- `PUT /api/v1/products/{id}`
- `PATCH /api/v1/products/{id}`
- `DELETE /api/v1/products/{id}`

## 3. Phương thức HTTP

- `GET`: Lấy thông tin tài nguyên mà không làm thay đổi trạng thái của tài nguyên trên máy chủ.
- `POST`: Tạo tài nguyên mới hoặc thực hiện thao tác phù hợp với phương thức này.
- `PUT`: Thay thế toàn bộ tài nguyên hiện có.
- `PATCH`: Cập nhật một phần tài nguyên.
- `DELETE`: Xóa tài nguyên.

Các dịch vụ phải sử dụng phương thức HTTP đúng với mục đích của thao tác. Không sử dụng `GET` để thực hiện thao tác làm thay đổi dữ liệu.

## 4. Định dạng request và response

- Sử dụng JSON cho nội dung request và response, trừ khi endpoint quy định định dạng khác.
- Sử dụng header `Content-Type: application/json` khi gửi dữ liệu JSON.
- Sử dụng mã hóa UTF-8.
- Thống nhất cách đặt tên trường dữ liệu giữa các dịch vụ.
- Sử dụng dấu thời gian theo ISO 8601, có múi giờ rõ ràng; ưu tiên UTC.
- Không trả về các trường dữ liệu nội bộ không cần thiết cho client.
- Tài liệu API phải mô tả rõ cấu trúc request và response.

Ví dụ response:

```json
{
  "id": "123",
  "name": "Example product",
  "createdAt": "2026-01-01T10:00:00Z"
}
```

## 5. Mã trạng thái HTTP

Các dịch vụ cần sử dụng mã trạng thái HTTP phù hợp với kết quả xử lý yêu cầu.

- `200 OK`: Yêu cầu được xử lý thành công.
- `201 Created`: Tài nguyên đã được tạo thành công.
- `202 Accepted`: Yêu cầu đã được tiếp nhận để xử lý bất đồng bộ.
- `204 No Content`: Yêu cầu thành công nhưng không có nội dung phản hồi.
- `400 Bad Request`: Cú pháp hoặc tham số yêu cầu không hợp lệ.
- `401 Unauthorized`: Thiếu thông tin xác thực hoặc thông tin xác thực không hợp lệ.
- `403 Forbidden`: Người gọi đã xác thực nhưng không có quyền thực hiện thao tác.
- `404 Not Found`: Không tìm thấy tài nguyên.
- `409 Conflict`: Yêu cầu xung đột với trạng thái hiện tại của tài nguyên.
- `422 Unprocessable Entity`: Dữ liệu không vượt qua kiểm tra hợp lệ, nếu hệ thống áp dụng mã này.
- `429 Too Many Requests`: Vượt quá giới hạn số lượng yêu cầu.
- `500 Internal Server Error`: Lỗi máy chủ không mong muốn.
- `502 Bad Gateway`: Gateway nhận được phản hồi không hợp lệ từ dịch vụ phía sau.
- `503 Service Unavailable`: Dịch vụ tạm thời không khả dụng.
- `504 Gateway Timeout`: Dịch vụ phía sau phản hồi quá thời gian cho phép.

Không nên sử dụng mã `200` cho mọi trường hợp, kể cả khi yêu cầu thất bại. Phản hồi cần thể hiện đúng kết quả xử lý.

## 6. Cấu trúc phản hồi lỗi chuẩn

Các dịch vụ nên sử dụng cấu trúc JSON thống nhất khi trả về lỗi để client dễ xử lý và nhóm phát triển dễ truy vết.

Ví dụ:

```json
{
  "timestamp": "2026-01-01T10:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/products",
  "traceId": "example-trace-id",
  "details": [
    {
      "field": "name",
      "message": "Name is required"
    }
  ]
}
```

Ý nghĩa các trường:

- `timestamp`: Thời điểm phát sinh lỗi.
- `status`: Mã trạng thái HTTP.
- `code`: Mã lỗi dùng để phân loại lỗi.
- `message`: Mô tả ngắn gọn về lỗi.
- `path`: Đường dẫn API phát sinh lỗi.
- `traceId`: Mã dùng để truy vết yêu cầu giữa các dịch vụ.
- `details`: Danh sách chi tiết lỗi, nếu có.

Các trường có thể được điều chỉnh theo nhu cầu thực tế nhưng phải được thống nhất và tài liệu hóa giữa các dịch vụ.

Không đưa stack trace, mật khẩu, token, thông tin cá nhân nhạy cảm hoặc thông tin hạ tầng nội bộ vào phản hồi lỗi gửi cho client.

## 7. Kiểm tra dữ liệu đầu vào

- Kiểm tra các trường bắt buộc, định dạng, độ dài và phạm vi giá trị số.
- Kiểm tra kiểu dữ liệu và các ràng buộc nghiệp vụ trước khi xử lý.
- Từ chối dữ liệu không hợp lệ bằng mã trạng thái HTTP thuộc nhóm `4xx` phù hợp.
- Trả về thông báo lỗi rõ ràng, hữu ích nhưng không tiết lộ thông tin nhạy cảm.
- Không âm thầm chấp nhận hoặc tự ý biến đổi dữ liệu không hợp lệ nếu chưa có quy tắc rõ ràng.
- Không chỉ dựa vào kiểm tra ở phía client; máy chủ phải tự kiểm tra dữ liệu đầu vào.

## 8. Xác thực và phân quyền

- Các endpoint được bảo vệ phải sử dụng cơ chế xác thực đã được thống nhất trong dự án.
- Khi sử dụng Bearer Token, gửi token qua header `Authorization: Bearer <token>`.
- Trả về `401` khi thiếu hoặc có thông tin xác thực không hợp lệ.
- Trả về `403` khi người gọi đã xác thực nhưng không đủ quyền.
- Kiểm tra quyền truy cập ở phía máy chủ trước khi cho phép thực hiện thao tác.
- Không ghi access token, mật khẩu hoặc thông tin đăng nhập vào log.
- Không đưa token hoặc thông tin xác thực vào URL.
- Các endpoint công khai và endpoint được bảo vệ phải được xác định rõ trong tài liệu API.

## 9. API Gateway và CORS

- Các ứng dụng client nên sử dụng điểm truy cập API Gateway đã được cấu hình.
- API Gateway định tuyến yêu cầu đến dịch vụ phù hợp.
- Cấu hình rõ các origin, HTTP method và header được phép cho từng môi trường.
- Không sử dụng origin không giới hạn cùng thông tin xác thực trong môi trường production.
- Chuyển tiếp mã định danh tương quan hoặc mã truy vết khi có.
- Không để lộ thông tin nội bộ của các dịch vụ phía sau thông qua phản hồi lỗi của Gateway.
- Cấu hình timeout phù hợp cho các yêu cầu được chuyển tiếp đến dịch vụ phía sau.

## 10. Phân trang, lọc và sắp xếp

Các endpoint trả về danh sách nên sử dụng các tham số truy vấn thống nhất.

Các tham số đề xuất:

- `page`: Chỉ số trang, bắt đầu từ `0`.
- `size`: Số lượng phần tử trên mỗi trang.
- `sort`: Trường và chiều sắp xếp, ví dụ `name,asc`.
- Các bộ lọc theo tài nguyên, chẳng hạn `status` hoặc `available`.

Ví dụ:

```http
GET /api/v1/products?page=0&size=20&sort=name,asc
```

Các dịch vụ cần:

- Ghi rõ kích thước trang mặc định và kích thước tối đa.
- Kiểm tra các tham số phân trang và sắp xếp.
- Giới hạn kích thước trang để tránh truy vấn quá lớn.
- Thống nhất cấu trúc phản hồi phân trang khi có thể.
- Chỉ cho phép sắp xếp theo các trường được hỗ trợ.

## 11. Timeout, thử lại và tính idempotent

- Cấu hình rõ thời gian chờ kết nối và thời gian chờ phản hồi khi các dịch vụ gọi nhau.
- Chỉ thử lại những lỗi mà việc thử lại là an toàn.
- Giới hạn số lần thử lại và sử dụng khoảng chờ tăng dần để tránh gây quá tải cho các dịch vụ phụ thuộc.
- Không tự động thử lại các thao tác không có tính idempotent, trừ khi có cơ chế bảo vệ phù hợp.
- Cân nhắc sử dụng khóa idempotency cho các thao tác mà việc gửi trùng yêu cầu có thể gây hậu quả, chẳng hạn tạo đơn hàng hoặc thanh toán.
- Ghi lại các lỗi timeout và số lần thử lại để phục vụ giám sát.

Tính idempotent là tính chất mà việc thực hiện cùng một yêu cầu nhiều lần tạo ra kết quả trạng thái cuối tương đương với việc thực hiện một lần.

## 12. Ghi log và truy vết

- Ưu tiên sử dụng log có cấu trúc khi phù hợp.
- Đưa mã định danh request hoặc mã truy vết vào log.
- Chuyển tiếp mã truy vết giữa API Gateway và các dịch vụ phía sau khi có.
- Không ghi mật khẩu, token hoặc thông tin cá nhân nhạy cảm.
- Ghi lại lỗi với đủ ngữ cảnh để xử lý sự cố nhưng không tiết lộ thông tin bí mật.
- Ghi nhận các thông tin cần thiết như thời điểm, dịch vụ, thao tác và kết quả xử lý.
- Thống nhất cách sử dụng `traceId` giữa các dịch vụ để hỗ trợ truy vết xuyên suốt.

## 13. Phiên bản API và khả năng tương thích

- Sử dụng phiên bản API rõ ràng cho endpoint công khai, chẳng hạn `/api/v1`.
- Ưu tiên các thay đổi tương thích ngược trong cùng một phiên bản.
- Không đổi tên hoặc xóa trường response khi chưa có kế hoạch chuyển đổi.
- Ghi lại các thay đổi gây mất tương thích và phối hợp với những dịch vụ phụ thuộc.
- Khi cần thay đổi lớn, cân nhắc tạo phiên bản API mới.
- Cập nhật tài liệu và kiểm thử liên quan trước khi phát hành thay đổi.

## 14. Tài liệu và kiểm thử

Với mỗi endpoint, cần ghi rõ:

- Mục đích và phương thức HTTP.
- Đường dẫn, tham số đường dẫn và tham số truy vấn.
- Cấu trúc request body và response.
- Yêu cầu xác thực và phân quyền.
- Các mã trạng thái có thể trả về và cấu trúc phản hồi lỗi.
- Các giới hạn, quy tắc kiểm tra dữ liệu và hành vi đặc biệt nếu có.

Kiểm thử cần bao gồm:

- Trường hợp thành công.
- Dữ liệu đầu vào không hợp lệ.
- Thiếu thông tin xác thực hoặc không đủ quyền.
- Tài nguyên không tồn tại.
- Lỗi từ dịch vụ phụ thuộc hoặc timeout.
- Các tình huống biên và trường hợp gửi yêu cầu trùng khi phù hợp.

Các thay đổi API cần được kiểm thử trước khi hợp nhất vào nhánh chính.

## 15. Áp dụng quy ước

Các quy ước này là tiêu chuẩn chung cho các dịch vụ thuộc hệ thống ScentStation.

Những dịch vụ hiện có có thể cần được cập nhật theo từng giai đoạn. Mọi trường hợp ngoại lệ cần được ghi lại và thống nhất với nhóm phát triển.

Khi bổ sung dịch vụ hoặc endpoint mới, nhóm phát triển cần đối chiếu với tài liệu này. Các thay đổi đối với quy ước chung cần được xem xét, cập nhật tài liệu và thông báo đến các nhóm liên quan.

Mục tiêu là bảo đảm các dịch vụ có cách thiết kế API nhất quán, dễ tích hợp, dễ kiểm thử và dễ bảo trì.
