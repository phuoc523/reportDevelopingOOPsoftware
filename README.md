
# ScentStation  – IoT-Based Multi-Brand Smart Perfume Kiosk Management System

Thành Viên
- Phương   
- phước
- Hoàng Đình Phùng - 064205007188
- Phương
Phân công công việc ở docs
**ScentStation** là nền tảng IoT đa thương hiệu cho kiosk trải nghiệm nước hoa. Hệ thống hướng đến việc hỗ trợ khách hàng chọn nước hoa, thanh toán QR và theo dõi trạng thái xịt; đồng thời hỗ trợ nhân sự quản lý máy, sản phẩm, tồn kho, bảo trì, cảnh báo và báo cáo vận hành.

> **Trạng thái dự án:** Đang trong quá trình phân tích, thiết kế và phát triển. Công nghệ và cấu trúc bên dưới phản ánh định hướng trong tài liệu dự án; hãy đối chiếu với các file cấu hình thực tế trước khi chạy.

## Mục lục
- [Mục tiêu](#mục-tiêu)
- [Chức năng chính](#chức-năng-chính)
- [Kiến trúc](#kiến-trúc)
- [Công nghệ dự kiến](#công-nghệ-dự-kiến)
- [Cấu trúc repository](#cấu-trúc-repository)
- [Luồng nghiệp vụ](#luồng-nghiệp-vụ)
- [An toàn và bảo mật](#an-toàn-và-bảo-mật)
- [Bắt đầu phát triển](#bắt-đầu-phát-triển)
- [Tài liệu](#tài-liệu)
- [Đóng góp](#đóng-góp)

## Mục tiêu

- Xây dựng kiosk nước hoa có thể điều khiển và giám sát từ xa.
- Cung cấp trải nghiệm chọn nước hoa, thanh toán QR và theo dõi trạng thái xịt.
- Hỗ trợ nhiều thương hiệu (multi-tenant) với phân tách dữ liệu và quyền truy cập.
- Quản lý máy, slot, chai/lọ nước hoa, tồn kho, cảnh báo và bảo trì.
- Cung cấp dashboard, báo cáo, đối soát giao dịch và audit log.
- Thiết kế hệ thống dễ kiểm thử, bảo trì và mở rộng.

## Chức năng chính

### Khách hàng / Kiosk
- Xem nước hoa khả dụng, thông tin sản phẩm và giá.
- Chọn sản phẩm, xác nhận đơn hàng và thanh toán bằng QR.
- Theo dõi trạng thái thanh toán và quá trình xịt.
- Nhận hướng dẫn khi thanh toán thành công nhưng việc xịt thất bại hoặc chưa xác định được kết quả.

### Quản trị và vận hành
- Quản lý tài khoản, thương hiệu/tenant, vai trò và quyền.
- Quản lý sản phẩm, địa điểm, máy và slot.
- Quản lý chai/lọ hoặc cartridge, refill, thay thế và điều chỉnh tồn kho.
- Theo dõi trạng thái thiết bị, telemetry và cảnh báo.
- Quản lý phiếu bảo trì, checklist và lịch sử sửa chữa.
- Xem dashboard, báo cáo, đối soát và nhật ký kiểm toán.

## Kiến trúc

Dự án định hướng theo **SOA + Microservices**, được quản lý trong một **monorepo**. Mỗi microservice là một ứng dụng riêng, có cấu hình và Dockerfile riêng, đồng thời quản lý dữ liệu thuộc phạm vi trách nhiệm của mình.

```text
Admin Web ─────┐
               ├──> API Gateway ──> Identity Service
Kiosk Web ─────┘                ├─> Catalog Service
                                ├─> Order Service
                                ├─> Machine Service ──> MQTT Broker ──> Device / Simulator
                                ├─> Operation Service
                                └─> Reporting Service
```

### Backend services

| Thành phần | Trách nhiệm |
|---|---|
| `api-gateway` | Cổng HTTP chung và định tuyến request |
| `identity-service` | Tài khoản, tenant/brand, vai trò, quyền và xác thực |
| `catalog-service` | Danh mục nước hoa, địa điểm và chiến dịch |
| `order-service` | Đơn hàng, thanh toán, webhook, đối soát và quy trình hoàn tiền |
| `machine-service` | Máy, slot, trạng thái thiết bị, telemetry, MQTT và lệnh xịt |
| `operation-service` | Tồn kho, chai/lọ, refill, cảnh báo và bảo trì |
| `reporting-service` | Dashboard, báo cáo, xuất dữ liệu và audit log |

Frontend dự kiến gồm `admin-web` cho nhân sự vận hành và `kiosk-web` cho khách hàng. Khi chưa có phần cứng thật, `device/simulator` được dùng để mô phỏng máy.

## Công nghệ dự kiến

- **Backend:** Java, Spring Boot, Maven
- **Frontend:** React
- **IoT messaging:** MQTT
- **Containerization:** Docker, Docker Compose
- **Database:** MySQL, với vùng dữ liệu riêng cho từng service
- **API contract:** REST/OpenAPI và MQTT contracts
- **Version control:** Git, GitHub
- **Quản lý công việc/tài liệu:** Jira hoặc GitHub Projects; Confluence

Phiên bản và cấu hình chính thức cần được xác nhận trong các file cấu hình của repository.

## Cấu trúc repository

```text
scentstation/
├── services/
│   ├── api-gateway/
│   ├── identity-service/
│   ├── catalog-service/
│   ├── order-service/
│   ├── machine-service/
│   ├── operation-service/
│   └── reporting-service/
├── frontend/
│   ├── admin-web/
│   └── kiosk-web/
├── device/
│   ├── simulator/
│   └── firmware/
├── contracts/
│   ├── openapi/
│   ├── mqtt/
│   └── shared/
├── infra/
│   ├── docker/
│   ├── env/
│   └── scripts/
├── docs/
│   ├── architecture/
│   ├── usecase/
│   ├── database/
│   ├── sequence/
│   ├── security/
│   ├── testing/
│   └── deployment/
├── scripts/
├── docker-compose.yml
├── pom.xml
├── README.md
└── CONTRIBUTING.md
```

Đây là cấu trúc mục tiêu theo hướng dẫn triển khai. Nếu repository hiện tại chưa có đủ thư mục hoặc file, hãy cập nhật README theo tiến độ thực tế.

## Luồng nghiệp vụ

1. Khách hàng chọn nước hoa còn khả dụng trên kiosk.
2. Hệ thống tạo đơn hàng và hiển thị QR thanh toán.
3. Backend xác minh kết quả thanh toán từ payment provider.
4. Sau khi thanh toán được xác nhận, Order Service phối hợp với Machine Service để yêu cầu xịt.
5. Machine Service gửi lệnh qua MQTT đến thiết bị và theo dõi phản hồi.
6. Hệ thống hiển thị kết quả. Nếu kết quả xịt chưa rõ ràng, giao dịch cần được kiểm tra thay vì tự động xịt lại.

## An toàn và bảo mật

- Chỉ backend được xác nhận thanh toán thành công; ảnh chụp màn hình hoặc trạng thái trên kiosk không phải bằng chứng thanh toán.
- Mỗi đơn hàng đã thanh toán có tối đa **một lần xịt thành công**.
- Lệnh, webhook và thao tác thử lại phải chống xử lý trùng (idempotency) và truy vết được.
- Không xịt khi cửa máy mở, máy đang bảo trì, slot hết nước hoa hoặc có lỗi phần cứng nghiêm trọng.
- Kết quả xịt chưa rõ ràng phải được xem xét; không tự động phát lệnh xịt mới.
- Chỉ cập nhật tồn kho bán hàng khi kết quả xịt được xác nhận; xịt chẩn đoán được ghi nhận riêng.
- Dữ liệu giữa các thương hiệu phải được phân tách; quyền truy cập dựa trên vai trò và phạm vi được cấp.
- Các thao tác quan trọng như refill, điều chỉnh tồn kho, xịt thử và thay đổi cấu hình cần có audit log.
- Máy chỉ trở lại hoạt động sau khi hoàn thành checklist và kiểm tra sau bảo trì.

## Bắt đầu phát triển

### Yêu cầu môi trường
Cài đặt công cụ phù hợp với thành phần bạn phụ trách:
- Git
- JDK theo cấu hình Maven của dự án
- Maven (nếu không dùng Maven Wrapper)
- Node.js và npm cho frontend hoặc device simulator
- Docker và Docker Compose

### Lấy source code

```bash
git clone <REPOSITORY_URL>
cd scentstation
```

Thay `<REPOSITORY_URL>` bằng URL GitHub thực tế của repository.

### Chạy dự án
1. Đọc README trong service/frontend/simulator tương ứng.
2. Kiểm tra file cấu hình mẫu trong `infra/env/`.
3. Thiết lập các biến môi trường cần thiết.
4. Nếu `docker-compose.yml` đã được cấu hình đầy đủ, làm theo hướng dẫn chạy Docker Compose của nhóm.

Không commit mật khẩu, token, khóa ký hoặc thông tin thanh toán thật. Chỉ ghi lệnh chạy chính thức sau khi các image, port, biến môi trường và health check đã được kiểm tra.

## Tài liệu

Tài liệu nghiệp vụ và kỹ thuật được lưu trong `docs/`, dự kiến gồm:
- `architecture/`: kiến trúc và phân chia microservice.
- `usecase/`: Use Case Diagram và Use Case Specification.
- `database/`: ERD, schema và quy ước dữ liệu.
- `sequence/`: sequence diagram cho các luồng quan trọng.
- `security/`: xác thực, phân quyền và bảo mật thiết bị.
- `testing/`: kế hoạch, test case và kết quả kiểm thử.
- `deployment/`: cấu hình và hướng dẫn triển khai.

## Đóng góp

1. Đọc tài liệu kiến trúc và xác định service chịu trách nhiệm cho chức năng cần sửa.
2. Tạo branch riêng cho task.
3. Giữ thay đổi trong đúng service; không truy cập trực tiếp database của service khác.
4. Cập nhật API/MQTT contract và tài liệu khi thay đổi giao tiếp.
5. Bổ sung hoặc cập nhật test phù hợp.
6. Tạo Pull Request, mô tả thay đổi, cách kiểm thử và liên kết Jira issue liên quan.

Tên branch và commit message cần tuân theo quy ước chung của nhóm trong `CONTRIBUTING.md` nếu file này đã có.

## Thông tin dự án

- **Tên:** ScentStation
- **Loại dự án:** IoT, Smart Perfume Kiosk, Multi-tenant Platform
- **Repository:** Monorepo
- **Kiến trúc:** SOA + Microservices
- **Quản lý công việc:** Jira / GitHub Projects
- **Tài liệu:** Confluence / `docs/`
