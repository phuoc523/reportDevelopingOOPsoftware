# Identity Service

Identity là nguồn dữ liệu tài khoản, vai trò, phân quyền và hồ sơ thương hiệu. Service sử dụng Java 21, Spring Security, MySQL, BCrypt và JWT. Hồ sơ hiển thị có thể dùng Redis với TTL theo cấu hình.

Các luồng chính gồm đăng ký khách hàng, đăng nhập, quản lý phiên và phê duyệt vai trò nhân viên trong phạm vi tenant. Gateway công khai các đường dẫn Identity. Các service phía sau xác minh principal qua introspection nội bộ.

Xem [hướng dẫn Identity và Platform](../../docs/identity-platform.md) để chạy demo và test, [quy ước API](../../docs/api-conventions.md) và [sơ đồ UML](../../architecture/identity-service/README.md).
