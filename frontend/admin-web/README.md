# ScentStation Admin Web

## Chạy cục bộ

```powershell
npm.cmd install
npm.cmd run dev
```

Admin Web chạy tại `http://localhost:7514` và proxy `/api` tới API Gateway tại `http://127.0.0.1:8080`.

## Thông báo và Firebase Web Push

Hộp thông báo trong ứng dụng hoạt động qua Notification Service mà không cần Firebase. Sao chép `.env.example` thành `.env.local` và điền các biến `VITE_FIREBASE_*` nếu muốn bật nút đăng ký thông báo đẩy trên trình duyệt.

Backend Firebase mặc định bị tắt. Khi có service-account JSON, cấu hình `NOTIFICATION_FIREBASE_PROJECT_ID` và `NOTIFICATION_FIREBASE_CREDENTIALS_FILE` trong `.env`, sau đó chạy:

```powershell
docker compose -f docker-compose.yml -f infra/firebase/docker-compose.firebase.yml up -d --build
```

Không commit file service-account hoặc các Firebase key bí mật vào Git.
