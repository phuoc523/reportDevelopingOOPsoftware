# ScentStation Admin Web – role-based UI demo

## Phân vai
- Platform Super Administrator: tenant, người quản trị cấp brand, audit, báo cáo toàn hệ thống.
- Brand Administrator: sản phẩm, chiến dịch, cấu hình máy, nhân viên brand, báo cáo brand.
- Operations Manager: vận hành, cảnh báo, bảo trì, xem xét giao dịch.
- Technician: máy được phân công, chẩn đoán, bảo trì.
- Inventory Staff: nhập/xuất, điều chỉnh tồn kho có lý do, báo cáo kho.
- Finance (bổ sung theo yêu cầu): xem giao dịch, ghi chú đối soát, báo cáo tài chính. Không mặc định quyền hoàn tiền.

Customer/Kiosk User thuộc kiosk-web, không thuộc admin-web. Viewer không được cấp vai trò trong admin-web theo yêu cầu mới.

## Chưa triển khai backend
- Đăng nhập và chọn role vẫn là DEMO, không dùng cho production.
- Tenant isolation, JWT, phân quyền API, refund, người dùng, tenant CRUD, báo cáo dữ liệu thực phải tích hợp service tương ứng.
- Các màn hình mới (Thương hiệu, Người dùng, Báo cáo) là khung UI có mô tả nghiệp vụ, chưa phải CRUD thật.
- Không tin cậy việc ẩn nút/menu như bảo mật thực.

## Chạy
`npm.cmd install` rồi `npm.cmd run build` và `npm.cmd run dev -- --host 127.0.0.1 --port 3000`

## An toàn khi thay thế
Commit hoặc sao lưu nhánh hiện tại trước khi copy `src`. Không copy node_modules.

# Admin Web – phân cấp quản lý tài khoản (demo)

## Thay đổi
- `src/App.tsx`: Super Admin chỉ thấy Brand Administrator và thương hiệu tương ứng; Brand Admin chỉ thấy nhân sự thuộc thương hiệu BR-001 (demo). Thêm chọn thương hiệu khi tạo Brand Admin, chỉnh vai trò nhân sự, tìm kiếm, khóa/mở khóa có xác nhận.
- `src/App.css`: CSS cho phạm vi quản lý và chỉnh vai trò.
- `src/config/permissions.ts`: giữ nguyên 6 vai trò và ma trận quyền từ bản người dùng gửi, bao gồm Finance.

## Giới hạn quan trọng
- Tất cả thao tác mới chỉ cập nhật React state, không tồn tại sau tải lại trang và **không khóa tài khoản thực tế**.
- `managedTenantId` cố định `BR-001` để minh họa; cần thay bằng tenantId từ JWT/session sau khi xác thực.
- Phải triển khai Identity Service: API quản lý tài khoản, xác minh role và tenant ở backend, lưu trạng thái tài khoản, từ chối đăng nhập khi bị khóa, thu hồi/kiểm tra hiệu lực token hiện hành, ghi audit log.
- Tài khoản Brand Admin trong dữ liệu mẫu được gán BR-001 và BR-002. Brand Admin không được gán Super Admin/Brand Admin cho nhân viên.

## Cách áp dụng
Sao lưu/commit các thay đổi trước khi chép đè. Chép `src/App.tsx` và `src/App.css` vào `frontend/admin-web/src`, giữ nguyên các file còn lại. Chạy `npm.cmd run build` và `npm.cmd run dev` để xác minh.
