# ScentStation Admin Web — Hướng dẫn phân quyền & Kiểm thử tích hợp (P5.8–P5.18)

## 1. Cơ chế xác thực & phân quyền thực tế

Admin Web kết nối trực tiếp các backend service qua API Gateway (`http://localhost:8080/api/**`),
**không còn chế độ chọn vai trò demo**:

- **Đăng nhập / Đăng xuất:** `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`.
  JWT lưu trong `sessionStorage` và gửi qua header `Authorization: Bearer <token>`.
- **Hết phiên / Tài khoản bị khóa (`401`):** Tự động xóa token, phát sự kiện
  `scentstation:unauthorized`, quay về màn hình đăng nhập và hiển thị lý do lỗi.
- **Thay đổi quyền (`403`):** Tự đồng bộ lại `GET /api/auth/me` để cập nhật menu và quyền
  thao tác theo trạng thái mới nhất từ Identity Service.
- **Chặn truy cập trang trái phép:** Lọc menu theo `principal.grants`, đồng thời lắng nghe
  `hashchange` để chặn gõ trực tiếp URL hash (`#tenants`, `#audit`, ...).

## 2. Ma trận vai trò trên Admin Web

| Vai trò | Phạm vi quản trị chính |
|---|---|
| `PLATFORM_SUPER_ADMIN` | Quản lý thương hiệu (`/api/platform/tenants`), tài khoản cấp nền tảng & Brand Admin (`/api/platform/users`), audit toàn hệ thống, Dashboard & báo cáo toàn nền tảng. |
| `BRAND_ADMIN` | Hồ sơ thương hiệu của mình (`/api/brand/tenant`), quản lý nhân sự thuộc thương hiệu (`/api/brand/users`, chỉ gán `OPERATIONS_MANAGER`, `TECHNICIAN`, `INVENTORY_STAFF`), sản phẩm & chiến dịch, cấu hình máy/slot, tồn kho, đơn hàng, Dashboard, Giao dịch & Xuất báo cáo (`.xlsx`/`.csv`). |
| `OPERATIONS_MANAGER` | Giám sát máy/slot, điều khiển bật/tắt/bảo trì (`ENABLE`/`DISABLE`/`ENTER_MAINTENANCE`), xử lý cảnh báo, tạo & phân công phiếu bảo trì, xem đơn hàng & đối soát, Dashboard vận hành. |
| `TECHNICIAN` | Xem máy/phiếu bảo trì được giao (`Chỉ phiếu được giao cho tôi`), nhận việc, hoàn thành checklist, ghi kết quả sửa chữa (chẩn đoán, linh kiện, chi phí, ghi chú, ảnh minh chứng) và yêu cầu test spray có kiểm soát (`DIAGNOSTIC_SPRAY`). Không có quyền tự điều chỉnh tồn kho. |
| `INVENTORY_STAFF` | Nhập lô, tạo chai, refill/lắp/thay chai, điều chỉnh tồn kho bắt buộc lý do (`INVENTORY_ADJUST`), theo dõi cảnh báo chai gần hết/hết/hết hạn và Dashboard tồn kho. |

## 3. Chạy kiểm thử & build Admin Web

```powershell
cd frontend/admin-web
npm.cmd test
npm.cmd run build
npm.cmd run lint
```

### Phạm vi kiểm thử tự động (`tests/rbacAndApi.test.ts`)
- **P5.8:** Đăng nhập, gửi Bearer token, đăng xuất, xử lý `401` (xóa token + báo hết phiên/khóa tài khoản), xử lý `403` (đồng bộ lại quyền).
- **P5.9 & P5.10:** Kiểm tra ranh giới `brandAssignableRoles` và `brandDelegablePermissions`, tách biệt endpoint `/api/platform/users` vs `/api/brand/users`, và thao tác khóa tài khoản gọi API backend thật.
- **P5.12 – P5.17:** Kiểm tra hợp đồng gọi API điều khiển máy (`ENTER_MAINTENANCE`), điều chỉnh tồn kho bắt buộc lý do, Dashboard, Operational Report, Revenue Breakdown và xuất báo cáo `.xlsx` / `.csv`.
