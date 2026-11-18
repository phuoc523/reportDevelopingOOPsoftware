# Task 2 – Setup Apache NiFi Environment

## Mục tiêu

Chuẩn bị một môi trường Apache NiFi chạy độc lập bằng Docker Compose để có thể truy cập NiFi Web UI và tạo Process Group thử nghiệm. Task này chỉ thiết lập môi trường; không kết nối MySQL, tạo Processor hay triển khai luồng đồng bộ dữ liệu.

## Phạm vi thực hiện

Task 2 bao gồm cấu hình và xác minh môi trường Apache NiFi bằng Docker trên máy Windows 11. Không bao gồm kết nối NiFi với MySQL, JDBC, Processor, ETL Flow, luồng đồng bộ, tích hợp microservice hoặc thay đổi dữ liệu/database.

## Công nghệ và phiên bản

- Apache NiFi Docker image: `apache/nifi:2.4.0` (đã pin phiên bản, không dùng `latest`).
- Windows 11, Docker Desktop và Docker Compose; triển khai độc lập với Compose chính của ScentStation.
- NiFi Web UI dùng HTTPS trên cổng host `8443`, chỉ bind vào loopback máy phát triển.
- NiFi Single User Authentication với thông tin đăng nhập được cấp qua biến môi trường.

Tài liệu tham khảo:

- [Apache NiFi Docker image documentation](https://hub.docker.com/r/apache/nifi)
- [Apache NiFi Administration Guide – Single User Authentication](https://nifi.apache.org/docs/nifi-docs/html/administration-guide.html#single_user_identity_provider)
- [Apache NiFi Administration Guide – port configuration](https://nifi.apache.org/docs/nifi-docs/html/administration-guide.html#port-configuration)
- [Docker Compose variable interpolation](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/)
- [Docker volumes](https://docs.docker.com/engine/storage/volumes/)

## Cấu trúc file

```text
infra/nifi/
├── docker-compose.yml
├── .env.example
└── .env               # dùng cục bộ, bị gitignore
```

`infra/nifi/.env` là file biến môi trường đang dùng cục bộ; file này chứa thông tin đăng nhập, được quy tắc `.gitignore` hiện có bỏ qua và không được commit. `.env.example` chỉ là mẫu không chứa mật khẩu.

## Giải thích cấu hình Compose

File `infra/nifi/docker-compose.yml`:

- Dùng image cố định `apache/nifi:2.4.0`.
- Chỉ công bố HTTPS `127.0.0.1:8443:8443`; không dùng cổng 8080, không mở UI ra các giao diện mạng khác.
- Service là `nifi`; Compose tạo container `scentstation-nifi-nifi-1`.
- Đặt `NIFI_WEB_HTTPS_PORT=8443` và ánh xạ `NIFI_USERNAME` sang `SINGLE_USER_CREDENTIALS_USERNAME`, `NIFI_PASSWORD` sang `SINGLE_USER_CREDENTIALS_PASSWORD`.
- Bắt buộc phải cung cấp cả username và password; Compose dừng với thông báo lỗi nếu thiếu.
- Dùng `restart: unless-stopped` để Docker khởi động lại container sau sự cố hoặc khi Docker khởi động lại, trừ khi container đã được dừng chủ động.
- Dùng named volumes cho cấu hình/flow (`conf`), database, FlowFile, content, provenance, state và logs của NiFi. Các volume tồn tại sau khi container dừng hoặc được tạo lại.
- Compose project được đặt tên `scentstation-nifi`, không sửa Compose chính hay phụ thuộc vào service khác.

## Cấu hình biến môi trường

File `infra/nifi/.env` hiện được dùng để cấp `NIFI_USERNAME` và `NIFI_PASSWORD` cho Docker Compose. Không ghi giá trị username/password thực tế vào tài liệu, không commit `.env`, và không chia sẻ đầu ra Compose có thể hiển thị giá trị biến môi trường.

Khi thiết lập trên máy mới, chỉ tạo `.env` từ mẫu nếu file chưa tồn tại; chạy từ thư mục gốc repository trong PowerShell:

```powershell
if (-not (Test-Path .\infra\nifi\.env)) {
    Copy-Item .\infra\nifi\.env.example .\infra\nifi\.env
}
notepad .\infra\nifi\.env
```

Điền `NIFI_USERNAME` và `NIFI_PASSWORD` trong file cục bộ. Image NiFi yêu cầu password Single User tối thiểu 12 ký tự. Chọn thông tin đăng nhập trước lần chạy đầu tiên vì cấu hình NiFi được lưu trong volume.

## Khởi động, kiểm tra và dừng

Các lệnh sau chạy từ thư mục gốc repository trong PowerShell:

```powershell
# Kiểm tra phiên bản và khả năng kết nối Docker Desktop
docker --version
docker compose version
docker info

# Kiểm tra cấu hình Compose
docker compose --env-file .\infra\nifi\.env -f .\infra\nifi\docker-compose.yml config

# Tải image và khởi động riêng NiFi
docker compose --env-file .\infra\nifi\.env -f .\infra\nifi\docker-compose.yml up -d

# Kiểm tra trạng thái container
docker compose --env-file .\infra\nifi\.env -f .\infra\nifi\docker-compose.yml ps

# Xem 100 dòng log gần nhất
docker compose --env-file .\infra\nifi\.env -f .\infra\nifi\docker-compose.yml logs --tail 100

# Tạo lại container nếu thay đổi cấu hình
docker compose --env-file .\infra\nifi\.env -f .\infra\nifi\docker-compose.yml up -d --force-recreate

# Dừng NiFi, giữ nguyên các named volume
docker compose --env-file .\infra\nifi\.env -f .\infra\nifi\docker-compose.yml stop

# Khởi động lại sau khi stop
docker compose --env-file .\infra\nifi\.env -f .\infra\nifi\docker-compose.yml start

# Gỡ container/network của Compose; không thêm tùy chọn -v
docker compose --env-file .\infra\nifi\.env -f .\infra\nifi\docker-compose.yml down
```

`docker compose down` không xóa named volumes nếu không có `-v`. Không chạy `docker compose down -v` hoặc lệnh xóa volume.

## Truy cập Web UI và tạo Process Group thử nghiệm

1. Sau khi `docker compose ps` cho thấy service NiFi đang chạy, mở <https://localhost:8443/nifi>.
2. Trình duyệt có thể cảnh báo chứng chỉ TLS tự ký của môi trường phát triển cục bộ. Chỉ tiếp tục nếu địa chỉ vẫn là `localhost`.
3. Đăng nhập bằng username và password đã cấu hình trong `infra/nifi/.env`.
4. Trên canvas gốc của NiFi, chọn công cụ **Process Group**, kéo vào canvas và đặt tên `ScentStation-Task2-SmokeTest`, rồi xác nhận tạo.
5. Xác nhận Process Group mới xuất hiện trên canvas. Đây chỉ là kiểm tra giao diện; không thêm Processor hoặc kết nối dữ liệu.

## Kết quả kiểm tra và ảnh minh chứng

Các kết quả đã xác nhận:

- Lệnh `docker compose ... config` hoàn tất thành công.
- `docker compose ... ps` hiển thị container `scentstation-nifi-nifi-1`, service `nifi`, image `apache/nifi:2.4.0`, trạng thái `Up` và mapping `127.0.0.1:8443->8443/tcp`.
- Theo kết quả triển khai, đã truy cập `https://localhost:8443/nifi`, đăng nhập thành công và xác nhận Canvas hiển thị bình thường.
- Các bước tạo Process Group được hướng dẫn ở trên; chưa ghi nhận việc tạo Process Group là kết quả đã xác minh.

### Minh chứng hoàn thành

Các ảnh cần đính kèm Jira:

1. Docker Desktop hiển thị container NiFi đang chạy.
2. Terminal hiển thị `docker compose ps` với trạng thái `Up`.
3. NiFi Web UI sau khi đăng nhập thành công, Canvas hiển thị bình thường.

Tại thời điểm cập nhật tài liệu, chưa tìm thấy ảnh minh chứng NiFi trong repository; không có đường dẫn ảnh nào được ghi giả định. Không chụp hoặc chia sẻ `.env`, mật khẩu hay đầu ra cấu hình có chứa thông tin đăng nhập.

## Ngoài phạm vi Task 2

Không cấu hình JDBC Connection Pool hoặc kết nối MySQL; không tạo Processor, trích xuất/chuyển đổi/ghi dữ liệu, flow đồng bộ, Kafka hay tích hợp khác. Không thay đổi service, API, database hoặc Compose chính của ScentStation. Các nội dung này để dành cho Task tiếp theo.
