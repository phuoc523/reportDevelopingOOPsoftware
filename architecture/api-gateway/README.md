# CỬA NGÕ API GATEWAY SERVICE (:8080)

## 1. Giới thiệu phân hệ
`api-gateway` được xây dựng trên nền tảng Spring Cloud Gateway, đóng vai trò là chốt chặn duy nhất mở ra Internet của toàn hệ thống ScentStation:
- Định tuyến các request public từ Web, Mobile và màn hình cảm ứng Kiosk tới 6 microservices nghiệp vụ nội bộ theo đường dẫn URL.
- Cấu hình chia sẻ tài nguyên nguồn gốc chéo (`Global CORS Configuration`).
- Bảo vệ ranh giới an toàn mạng (*Trust Boundary Protection*) qua `GatewayTrustBoundaryFilter`: Tự động nhận diện và gỡ bỏ triệt để các Header nội bộ giả mạo (`X-Internal-Service-Key`, `X-Actor-Id`, `X-Actor-Grants`) trước khi chuyển tiếp vào mạng microservices, ngăn ngừa tuyệt đối nguy cơ leo thang đặc quyền từ bên ngoài.

## 2. Danh mục sơ đồ chi tiết
1. **`routing-architecture.puml`**:
   - Thể hiện cấu trúc các bộ lọc: Client $\rightarrow$ `GatewayTrustBoundaryFilter` $\rightarrow$ `GlobalCorsFilter` $\rightarrow$ `RoutePredicateHandlerMapping` $\rightarrow$ 6 Microservices.
2. **`trust-boundary-sequence.puml`**:
   - Minh họa quy trình thanh lọc Header bảo mật: Kịch bản kẻ tấn công cố tình tiêm Header nội bộ bị Gateway bóc tách và vô hiệu hóa, và kịch bản người dùng hợp lệ được cấp phát mã vết `X-Request-Id`.
