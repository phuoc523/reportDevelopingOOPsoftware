1. Bảng phân công mới cho 5 thành viên
Phương án đề xuất: chia theo 5 nhóm công việc, giới hạn phạm vi tính năng nâng cao và phân bổ trách nhiệm tích hợp cho cả nhóm.
Thành viên	Phụ trách	Thư mục chính
Người 1	Identity, API Gateway và nền tảng chung	identity-service/, api-gateway/
Người 2	Catalog và Inventory	catalog-service/, phần tồn kho trong operation-service/
Người 3	Order và Payment	order-service/
Người 4	Machine và IoT	machine-service/, device/
Người 5	Frontend và Reporting	customer-web/, admin-web/, reporting-service/

Tiêu chí hỗ trợ lẫn nhau chỉ phân nhánh riêng chỉ làm trong thời gian ngắn của 1/10 thời gian
2. Chi tiết công việc từng người

Người 1 – Identity & Platform

Nhiệm vụ
- Đăng ký, đăng nhập, đăng xuất.
- Quản lý tài khoản và phân quyền Admin, Staff, Customer.
- Xác thực và bảo vệ API.
- Thiết lập API Gateway, định tuyến request và cấu hình CORS cơ bản.
- Thống nhất cấu hình chung, cách xác thực và quy ước lỗi API với các thành viên.
Bàn giao: Identity Service, Gateway chạy được, tài liệu xác thực và test quyền truy cập.
Giới hạn: Không phụ trách Docker toàn hệ thống hoặc tích hợp toàn bộ ứng dụng.



Người 2 – Catalog & Inventory

Nhiệm vụ
- Quản lý nước hoa, thương hiệu, hình ảnh và giá bán.
- Xây dựng API thêm, sửa, xóa, xem sản phẩm.
- Quản lý tồn kho và số lượng nước hoa trong từng slot ở góc độ nghiệp vụ.
- Ghi nhận nạp hàng, điều chỉnh tồn kho và lịch sử thay đổi.
- Cung cấp dữ liệu sản phẩm, tồn kho cho frontend và báo cáo.
- Phối hợp với người 4 về thông tin slot và trạng thái thiết bị.
Thư mục: services/catalog-service/ và phần tồn kho được thống nhất trong services/operation-service/.
Bàn giao: API sản phẩm, API tồn kho, dữ liệu mẫu và test cập nhật số lượng.
Giới hạn: Chỉ phụ trách nghiệp vụ tồn kho; không xây dựng MQTT hoặc logic điều khiển máy.



Người 3 – Order & Payment

Nhiệm vụ
- Tạo và quản lý đơn hàng.
- Lưu giá sản phẩm tại thời điểm đặt hàng và tổng tiền.
- Xây dựng thanh toán QR mô phỏng hoặc tích hợp phương thức thanh toán được nhóm thống nhất.
- Xác nhận thanh toán ở backend, xử lý callback và yêu cầu trùng.
- Quản lý trạng thái đơn hàng.
- Gửi yêu cầu thực hiện đơn đã thanh toán sang Machine Service qua API hoặc message contract đã thống nhất.
Thư mục: services/order-service/
Bàn giao: API đơn hàng, xử lý thanh toán, sơ đồ trạng thái và test các trường hợp thành công/thất bại.
Giới hạn: Chưa cần xây dựng hệ thống hoàn tiền phức tạp nếu đề bài không bắt buộc.



Người 4 – Machine & IoT

Nhiệm vụ
- Quản lý thông tin máy, slot và trạng thái thiết bị.
- Thiết lập giao tiếp MQTT và các message contract.
- Xử lý heartbeat, nhận lệnh và phản hồi ACK.
- Xây dựng Device Simulator nếu không có máy thật.
- Kiểm tra các điều kiện trước khi xịt: máy ngoại tuyến, đang bảo trì, slot trống hoặc có lỗi phần cứng.
- Chống thực hiện lệnh trùng; kết quả không xác định phải chuyển sang đối soát, không tự động xịt lại.
Thư mục: services/machine-service/, device/.
Bàn giao: API quản lý máy, MQTT, Device Simulator và test các trường hợp lỗi chính.
Giới hạn: Chỉ triển khai giao thức và mô phỏng cần thiết cho demo; không mở rộng sang hệ thống IoT giám sát nâng cao.



Người 5 – Frontend & Reporting

Nhiệm vụ
- Xây dựng Customer Web: danh sách sản phẩm, chi tiết sản phẩm, giỏ hàng hoặc lựa chọn sản phẩm, tạo đơn và xem trạng thái.
- Xây dựng Admin Web: quản lý sản phẩm, đơn hàng, máy và tồn kho thông qua API.
- Kết nối frontend với API của các thành viên.
- Xây dựng Dashboard và báo cáo cơ bản từ dữ liệu API.
- Viết test giao diện và kiểm thử các luồng người dùng chính.
Thư mục: frontend/customer-web/, frontend/admin-web/, services/reporting-service/.
Bàn giao: Hai giao diện hoạt động, Dashboard cơ bản, hướng dẫn sử dụng và kết quả test luồng người dùng.
Giới hạn: Không phải người duy nhất chịu trách nhiệm tích hợp toàn hệ thống; mỗi thành viên phải tự kiểm thử và cung cấp hướng dẫn chạy module của mình.


3. Chia trách nhiệm tích hợp để không dồn việc cho người 5
Đây là thay đổi quan trọng nhất để cân bằng công việc.
Công việc chung	Người chịu trách nhiệm chính
Cấu trúc repository, quy ước API và xác thực	Người 1 điều phối
Tích hợp sản phẩm và tồn kho	Người 2
Tích hợp thanh toán và trạng thái đơn	Người 3
Tích hợp MQTT và Device Simulator	Người 4
Tích hợp giao diện và báo cáo	Người 5
Docker Compose	Người 1 phụ trách cấu hình Gateway; cả nhóm cung cấp cấu hình chạy module, một người được nhóm thống nhất ghép file Compose
Kiểm thử đầu cuối	Cả 5 người cùng thực hiện
Người 5 không cần tự sửa lỗi backend thay cho các thành viên khác. Người phụ trách từng module phải chịu trách nhiệm sửa lỗi trong phần của mình.
4. Quy ước GitHub cho cả nhóm
Mỗi thành viên làm việc trên branch riêng:
Thành viên	Branch
Người 1	feature/identity-platform
Người 2	feature/catalog-inventory
Người 3	feature/order-payment
Người 4	feature/machine-iot
Người 5	feature/frontend-reporting
Quy tắc bắt buộc:
- Không commit trực tiếp lên main.
- Mỗi tính năng được phát triển trên branch phụ trách.
- Tạo Pull Request và review trước khi merge.
- Thống nhất API contract trước khi code song song.
- Mỗi người có trách nhiệm viết test, cập nhật tài liệu và sửa lỗi thuộc module của mình.

