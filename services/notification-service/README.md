# Notification Service

Notification Service quản lý hộp thư thông báo theo tenant và người nhận. Service chạy tại cổng `8087`, dùng MySQL riêng và chỉ tin cậy tenant, quyền và người nhận sau khi Identity Service xác nhận.

## Chức năng chính

- Tạo thông báo có idempotency theo `tenantId + source + sourceEventId`.
- Danh sách, chi tiết, phân trang và lọc theo loại hoặc độ ưu tiên.
- Trạng thái đã đọc riêng cho từng người nhận; hỗ trợ đánh dấu một hoặc nhiều thông báo.
- Tenant isolation và RBAC qua `NOTIFICATION_READ` / `NOTIFICATION_CONFIGURE`.
- Đăng ký, kiểm tra và thu hồi Firebase device token theo đúng tenant/người dùng.
- Hàng đợi FCM bền vững trong MySQL, retry theo exponential backoff và lease để khôi phục job bị gián đoạn.
- Tự vô hiệu hóa token FCM không còn hợp lệ.
- Flyway migration, readiness/liveness probe và graceful shutdown.
- Lỗi Identity hoặc database được fail-closed và trả response an toàn.

Firebase và Kafka là adapter tùy chọn, có thể bật/tắt độc lập. Hộp thư trong ứng dụng vẫn hoạt động khi hai adapter này tắt.

## API công khai qua Gateway

- `POST /api/notifications`
- `GET /api/notifications`
- `GET /api/notifications/{id}`
- `PATCH /api/notifications/{id}/read`
- `PATCH /api/notifications/read`
- `GET /api/notifications/unread-count`
- `POST /api/notification-device-tokens`
- `GET /api/notification-device-tokens/{deviceId}`
- `DELETE /api/notification-device-tokens/{deviceId}`

## Cấu hình Firebase

Backend cần Firebase service account thật; Admin Web cần Firebase Web config và VAPID key của cùng project. Không commit các giá trị này vào Git.

```env
NOTIFICATION_FIREBASE_ENABLED=true
NOTIFICATION_FIREBASE_PROJECT_ID=your-project-id
NOTIFICATION_FIREBASE_CREDENTIALS_FILE=D:/secrets/firebase-service-account.json
NOTIFICATION_FIREBASE_POLL_INTERVAL=5s
NOTIFICATION_FIREBASE_BATCH_SIZE=20
NOTIFICATION_FIREBASE_MAX_ATTEMPTS=5
NOTIFICATION_FIREBASE_INITIAL_BACKOFF=10s
NOTIFICATION_FIREBASE_DELIVERY_LEASE=5m
```

Chạy Docker với file compose bổ sung để mount credential ở chế độ chỉ đọc:

```powershell
docker compose -f docker-compose.yml -f infra/firebase/docker-compose.firebase.yml up -d --build notification-service api-gateway
```

## Chạy và kiểm thử

```powershell
cd services/notification-service
mvn test
mvn -DskipTests package

cd ../..
docker compose build notification-service
docker compose up -d notification-service api-gateway
docker compose ps notification-service
```

Health endpoints: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`.
