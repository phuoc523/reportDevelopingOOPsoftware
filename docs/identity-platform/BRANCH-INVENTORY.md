# Kiểm kê nhánh GitHub

Giữ `cleanup/phung-identity-platform` đến khi nhóm review xong. Với ứng viên đã nằm trong lịch sử main, chỉ cân nhắc xóa sau khi chủ nhánh xác nhận nhánh đã hết nhiệm vụ và các PR liên quan đã được xử lý. Với nhánh có commit riêng, giữ hoặc lưu trữ SHA đầu nhánh trước khi thảo luận xóa.

## Đối chiếu nội dung Người 1

| Nguồn | Trạng thái trên baseline và nhánh cleanup |
|---|---|
| `feature/p1-03-auth-session` và `test-identity-update` | Main có đăng ký, BCrypt, JWT, khóa thủ công và thu hồi phiên. Nhánh P1-03 cũ có đếm lần đăng nhập sai và lưu phiên opaque vào các bảng riêng. Tự động khóa theo tài khoản và lịch sử phiên riêng cần thiết kế lại theo mô hình JWT hiện tại cùng quyết định về dữ liệu. |
| `feature/p1-04-rbac` và `feature/p1-05-tenant-isolation` | Main có nhiều vai trò, grants, tenant và phạm vi. Cleanup phục hồi quy tắc máy được phân công trong Identity và bảo đảm nhân viên chờ duyệt có phiên không mang quyền vận hành. |
| `feature/p1-06-p1-11-platform` | Main có Gateway, request ID, CORS, ranh giới tin cậy, audit của Identity và Notification Service riêng. API và bảng cấu hình platform/tenant kiểu cũ cùng API audit ghi sự kiện dùng chung cần thống nhất schema và hợp đồng. Notification tiếp tục dùng service hiện hành. |
| `feature/OOP-15-shared-api-conventions` và `docs/OOP-8-phung-identity` | Đã đưa tài liệu quy ước vào cleanup và cập nhật đường dẫn, mã trạng thái và định dạng thực tế. Giữ các alias và định dạng URL mới dưới dạng đề xuất khi nhóm cần versioning. |
| Các nhánh Redis và full-demo | Main đã có Redis profile cache theo tenant, rate limit ở Gateway và Docker overrides. Cleanup dùng lại implementation hiện hành và tinh gọn cấu hình của Identity/Gateway. Các thay đổi payment, device, Redis của service khác và script full-demo thuộc phạm vi tích hợp nhóm. |

Baseline `origin/main` tại `327a105`. Kiểm kê 51 nhánh remote. Cột phạm vi đếm commit riêng phía nhánh sau khi loại merge và các patch tương đương, giới hạn theo các đường dẫn Identity, Gateway, UML và tài liệu liên quan.

| Nhánh | Commit đầu | Đi trước main | Phạm vi Người 1 | Đề xuất |
|---|---|---:|---:|---|
| BE-FE-full-services | 38e4e90 | 0 | 0 | Giữ theo yêu cầu |
| OOP-3-test-connect-jira | b03a6ff | 0 | 0 | Ứng viên lưu trữ sau khi nhóm xác nhận |
| checkpoint/order-payment-pending-test-qr | 23d765c | 0 | 0 | Ứng viên lưu trữ sau khi nhóm xác nhận |
| docs/OOP-8-phung-identity | 586f105 | 11 | 8 | Lưu trữ sau khi đối chiếu |
| docs/identity-login-verification-20261115 | e0f9299 | 0 | 0 | Ứng viên lưu trữ sau khi nhóm xác nhận |
| feature/OOP-11-identity-platform | e5921a3 | 0 | 0 | Ứng viên lưu trữ sau khi nhóm xác nhận |
| feature/OOP-11-p102-account-lifecycle | eb435bd | 0 | 0 | Ứng viên lưu trữ sau khi nhóm xác nhận |
| feature/OOP-12-api-gateway | 573d7d7 | 0 | 0 | Ứng viên lưu trữ sau khi nhóm xác nhận |
| feature/OOP-13-role-based-authorization-final | 1949579 | 0 | 0 | Ứng viên lưu trữ sau khi nhóm xác nhận |
| feature/OOP-14-api-gateway | efe39d8 | 0 | 0 | Ứng viên lưu trữ sau khi nhóm xác nhận |
| feature/OOP-15-shared-api-conventions | 3a9f39f | 2 | 2 | Lưu trữ sau khi đối chiếu |
| feature/catalog | cd8e7b1 | 1 | 0 | Giữ để chủ nhánh quyết định |
| feature/inventory | 203b59c | 0 | 0 | Giữ để chủ nhánh quyết định |
| feature/machine-iot | b452e21 | 0 | 0 | Giữ để chủ nhánh quyết định |
| feature/maintenance | 8d8cee1 | 1 | 0 | Giữ để chủ nhánh quyết định |
| feature/member-5-admin-frontend | 78ca9bd | 0 | 0 | Giữ để chủ nhánh quyết định |
| feature/member-5-kiosk-frontend | a2650ba | 0 | 0 | Giữ để chủ nhánh quyết định |
| feature/member-5-reporting-service | 120126a | 0 | 0 | Giữ để chủ nhánh quyết định |
| feature/merge-branch | c8ee009 | 0 | 0 | Ứng viên lưu trữ sau khi nhóm xác nhận |
| feature/monitoring | ec023e6 | 1 | 1 | Giữ để chủ nhánh quyết định |
| feature/nifi | 9d69a54 | 4 | 0 | Giữ để chủ nhánh quyết định |
| feature/order-payment | 1566939 | 0 | 0 | Giữ để chủ nhánh quyết định |
| feature/p1-03-auth-session | df34511 | 2 | 2 | Lưu trữ sau khi đối chiếu |
| feature/p1-04-rbac | 0cac2cd | 3 | 3 | Lưu trữ sau khi đối chiếu |
| feature/p1-05-tenant-isolation | 477d60c | 5 | 5 | Lưu trữ sau khi đối chiếu |
| feature/p1-06-p1-11-platform | 3516a07 | 8 | 6 | Lưu trữ sau khi đối chiếu |
| feature/phung-full-demo-backup-20261122 | 80e4fee | 3 | 0 | Lưu trữ sau khi đối chiếu |
| feature/phung-full-demo-oneclick | 80e4fee | 3 | 0 | Lưu trữ sau khi đối chiếu |
| feature/phung-scentstation-full-demo-lite | 2e1d3f8 | 14 | 2 | Lưu trữ sau khi đối chiếu |
| feature/phung-sepay-mb-real-payment | 61bb984 | 13 | 2 | Lưu trữ sau khi đối chiếu |
| feature/redis-all-services-phuoc | 1e4f62e | 1 | 1 | Lưu trữ sau khi đối chiếu |
| feature/redis-cache | 8483124 | 1 | 1 | Lưu trữ sau khi đối chiếu |
| feature/redis-full-cache-phuong | 312d6ae | 2 | 2 | Lưu trữ sau khi đối chiếu |
| feature/redis-use-all-services-phung | 9e318d0 | 0 | 0 | Ứng viên lưu trữ sau khi nhóm xác nhận |
| integration/be-fe-technologies | c85219e | 0 | 0 | Ứng viên lưu trữ sau khi nhóm xác nhận |
| integrationfullstack | e85dfec | 0 | 0 | Ứng viên lưu trữ sau khi nhóm xác nhận |
| main | 327a105 | 0 | 0 | Giữ theo yêu cầu |
| order-qr-option-2 | 8b2fa3c | 0 | 0 | Ứng viên lưu trữ sau khi nhóm xác nhận |
| test | 712dc9a | 10 | 7 | Lưu trữ sau khi đối chiếu |
| test-2026-11-07-latest | 1a0ebc0 | 0 | 0 | Ứng viên lưu trữ sau khi nhóm xác nhận |
| test-2026-11-13 | b260dbe | 0 | 0 | Ứng viên lưu trữ sau khi nhóm xác nhận |
| test-2026-11-26 | 150c9f5 | 5 | 0 | Lưu trữ sau khi đối chiếu |
| test-27-11-2026 | 327a105 | 0 | 0 | Ứng viên lưu trữ sau khi nhóm xác nhận |
| test-identity-update | 9ebd4df | 0 | 0 | Ứng viên lưu trữ sau khi nhóm xác nhận |
| test-iot-payment-20261125 | 29733ef | 1 | 0 | Lưu trữ sau khi đối chiếu |
| test-machine-iot | c8ee009 | 0 | 0 | Giữ để chủ nhánh quyết định |
| test-notification-service | 26bf8a5 | 0 | 0 | Giữ để chủ nhánh quyết định |
| test-order-payment | e537d66 | 3 | 1 | Giữ để chủ nhánh quyết định |
| test-order-qr-option-1 | 9016ae6 | 2 | 0 | Giữ để chủ nhánh quyết định |
| test-order-qr-option-2 | 6c89fe0 | 0 | 0 | Giữ để chủ nhánh quyết định |
| test-phung-iot-payment-all-20261125 | 06dd8c4 | 3 | 0 | Lưu trữ sau khi đối chiếu |
