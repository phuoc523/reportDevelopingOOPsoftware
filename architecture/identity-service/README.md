# PHÂN HỆ IDENTITY & MULTI-TENANCY SERVICE (:8081)

## 1. Giới thiệu phân hệ
`identity-service` là trung tâm xác thực và kiểm soát quyền truy cập của nền tảng ScentStation:
- Sở hữu cơ sở dữ liệu `identity_db`.
- Đảm nhận cấp phát JWT token có chữ ký số bí mật, hỗ trợ đa đối tác (`Tenant` / `TenantProfile`), vai trò (`Role`) và quyền hạn cụ thể (`Permission`).
- Cung cấp endpoint nội bộ `/internal/v1/identity/introspect` phục vụ kiểm tra token phân tán giữa các microservices.

## 2. Danh mục sơ đồ chi tiết
1. **`use-case.puml`**:
   - Thể hiện các ca sử dụng quản trị người dùng, phân quyền RBAC, quản lý tenant đối tác, đăng nhập, phát hành JWT và tra cứu audit log.
   - Các quan hệ: `<<include>>` cho luồng login buộc phải phát hành token và log audit.
2. **`class-diagram.puml`**:
   - Thực thể chính: `User`, `Role`, `Permission`, `Tenant`, `TenantProfile`, `AuditEvent`.
   - Quan hệ: `Tenant` *-- `TenantProfile` (Composition), `Tenant` o-- `User` (Aggregation), `Role` *-- `Permission` (Composition).
   - Tầng điều khiển & nghiệp vụ: `AuthController`, `TenantController`, `InternalIntrospectionController`, `AuthService`, `JwtService`, `AccessPolicy`.
3. **`sequence-diagram.puml`**:
   - Thể hiện 2 luồng sống còn: (1) Đăng nhập và mã hóa JWT (với các nhánh rẽ lỗi 401), (2) Downstream service gọi introspect qua internal service key.
4. **`activity-diagram.puml`**:
   - Phân làn chi tiết các quyết định: Tài khoản tồn tại $\rightarrow$ Kiểm tra trạng thái Active $\rightarrow$ Đối chiếu BCrypt hash $\rightarrow$ Cấp token và ghi log.
