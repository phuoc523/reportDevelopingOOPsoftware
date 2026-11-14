# HỆ THỐNG TÀI LIỆU SƠ ĐỒ THIẾT KẾ KIẾN TRÚC UML - SCENTSTATION

> Thư mục này tập hợp toàn bộ bộ sơ đồ chuẩn UML được phân bổ theo từng Microservice cụ thể của nền tảng **ScentStation**, được khảo sát và đối soát trực tiếp từ mã nguồn thực tế.

---

## 📁 CẤU TRÚC PHÂN BỔ THƯ MỤC THEO SERVICE

```text
architecture/
├── README.md                                  <-- Tài liệu hướng dẫn & tra cứu tổng hợp
│
├── system-overview/                           <-- Toàn cảnh hệ thống
│   ├── system-architecture.puml               <-- Sơ đồ kiến trúc 4 tầng, routing & database isolation
│   ├── system-use-case.puml                   <-- Sơ đồ ca sử dụng toàn hệ thống phân cấp Actor
│   ├── core-flow-sequence.puml                <-- Chu trình nghiệp vụ chính: Đặt hàng - Thanh toán - Phun hương
│   └── README.md
│
├── api-gateway/                               <-- Cửa ngõ định tuyến & bảo vệ ranh giới (:8080)
│   ├── routing-architecture.puml              <-- Cấu trúc định tuyến Spring Cloud Gateway & CORS
│   ├── trust-boundary-sequence.puml           <-- Cơ chế lọc và gỡ bỏ header nội bộ giả mạo
│   └── README.md
│
├── identity-service/                          <-- Phân hệ định danh & đa đối tác (:8081)
│   ├── use-case.puml                          <-- Ca sử dụng xác thực, RBAC, tenant & audit
│   ├── class-diagram.puml                     <-- Sơ đồ lớp User, Role, Permission, Tenant, AuditEvent
│   ├── sequence-diagram.puml                  <-- Luồng đăng nhập JWT & Introspection nội bộ
│   ├── activity-diagram.puml                  <-- Hoạt động kiểm tra BCrypt & cấp phát Access Token
│   └── README.md
│
├── catalog-service/                           <-- Phân hệ danh mục & giá khuyến mãi (:8082)
│   ├── use-case.puml                          <-- Ca sử dụng sản phẩm, chiến dịch & tính giá
│   ├── class-diagram.puml                     <-- Sơ đồ lớp Product, Campaign, DiscountType, Snapshot
│   ├── sequence-diagram.puml                  <-- Luồng chốt giá chuẩn Authoritative cho Order Service
│   ├── activity-diagram.puml                  <-- Hoạt động áp dụng chiết khấu % / cố định
│   └── README.md
│
├── machine-service/                           <-- Phân hệ trạm máy Kiosk & điều khiển phần cứng (:8083)
│   ├── use-case.puml                          <-- Ca sử dụng máy Kiosk, khay cắm (Slot), cảnh báo cảm biến
│   ├── class-diagram.puml                     <-- Sơ đồ lớp Machine, Slot, Alert, Outbox, Command
│   ├── sequence-diagram.puml                  <-- Luồng thực thi lệnh phun nước hoa at-most-once & xử lý lỗi
│   ├── activity-diagram.puml                  <-- Hoạt động kích bơm, đo cảm biến & cảnh báo hỏng hóc
│   └── README.md
│
├── order-service/                             <-- Phân hệ đơn hàng, thanh toán & điều phối (:8084)
│   ├── use-case.puml                          <-- Ca sử dụng tạo đơn, sinh QR, webhook IPN, review lỗi
│   ├── class-diagram.puml                     <-- Sơ đồ lớp Order, OrderItem, Payment, DispensingCommand, Review
│   ├── sequence-diagram.puml                  <-- Luồng webhook callback thanh toán & điều phối máy phun
│   ├── activity-diagram.puml                  <-- Hoạt động kiểm tra tính khả dụng, chốt giá & xử lý ngoại lệ
│   └── README.md
│
├── operation-service/                         <-- Phân hệ hậu cần kho & bảo trì kỹ thuật (:8085)
│   ├── use-case.puml                          <-- Ca sử dụng lô hàng, chai hương, lắp đặt, bảo trì, kiểm định
│   ├── class-diagram.puml                     <-- Sơ đồ lớp Batch, Bottle, Installation, Ticket, Checklist, Test
│   ├── sequence-diagram.puml                  <-- Luồng lắp đặt chai vào máy & kiểm định đóng phiếu bảo trì
│   ├── activity-diagram.puml                  <-- Hoạt động đối soát lắp đặt & vòng lặp nghiệm thu sửa chữa
│   └── README.md
│
└── reporting-service/                         <-- Phân hệ báo cáo & phân tích số liệu (:8086)
    ├── use-case.puml                          <-- Ca sử dụng đồng bộ giao dịch, dashboard & xuất Excel
    ├── class-diagram.puml                     <-- Sơ đồ lớp Transaction, DashboardResponse, ExcelService
    ├── sequence-diagram.puml                  <-- Luồng nhận đồng bộ giao dịch & xuất bảng tính Excel
    ├── activity-diagram.puml                  <-- Hoạt động thẩm định dữ liệu & kết xuất báo cáo POI
    └── README.md
```

---

## 🎨 HƯỚNG DẪN REVIEW VÀ CHUYỂN ĐỔI SANG BẢN VẼ TAY

### 1. Quy ước đường nét trong Sơ đồ Lớp (Class Diagram)
- **Kế thừa (Generalization)**: Đường nét liền, mũi tên hình tam giác rỗng (`──▷`). Dùng cho kế thừa enum/class.
- **Hiện thực (Realization)**: Đường nét đứt, mũi tên hình tam giác rỗng (`┈▷`). Dùng khi Service cài đặt Interface Client.
- **Thành phần (Composition)**: Đường nét liền, biểu tượng quả trám **tô đen đặc** ở phía lớp sở hữu (`◆──`). Biểu thị quan hệ sống còn (Cascade Delete):
  - `Order` sở hữu `OrderItem`.
  - `Machine` sở hữu `MachineSlot`.
  - `Tenant` sở hữu `TenantProfile`.
  - `InventoryBatch` sở hữu `Bottle`.
  - `MaintenanceTicket` sở hữu `MaintenanceChecklistItem` và `PostMaintenanceTest`.
- **Thu nạp (Aggregation)**: Đường nét liền, biểu tượng quả trám **rỗng** (`◇──`). Biểu thị quan hệ chứa lỏng:
  - `Location` chứa `Machine`.
  - `Tenant` chứa `User`.
- **Liên kết định hướng (Directed Association)**: Đường nét liền có mũi tên nhọn (`──>`).
- **Phụ thuộc (Dependency)**: Đường nét đứt có mũi tên nhọn (`┈>`).

### 2. Quy ước trong Sơ đồ Ca sử dụng (Use Case Diagram)
- Mũi tên `<<include>>`: Đường nét đứt có nhãn `<<include>>` trỏ từ ca sử dụng chính sang ca sử dụng thành phần bắt buộc.
- Mũi tên `<<extend>>`: Đường nét đứt có nhãn `<<extend>>` trỏ từ ca sử dụng mở rộng (xử lý ngoại lệ, sự cố) về ca sử dụng cơ sở.
- Thừa kế Actor: Mũi tên tam giác rỗng (`──▷`) từ Actor con trỏ về Actor cha (`AuthUser`).

### 3. Quy ước trong Sơ đồ Tuần tự (Sequence Diagram)
- Hộp kích hoạt (Lifeline Activation Box) biểu thị khoảng thời gian Component đang xử lý CPU.
- Mũi tên nét liền (`->`): Gọi đồng bộ (HTTP Request / Method Call).
- Mũi tên nét đứt (`-->`): Phản hồi dữ liệu (HTTP Response 200 OK / DTO).
- Khung `alt`: Rẽ nhánh điều kiện logic If / Else (nhánh lỗi 401, 404, 409 hoặc thành công).

---
*Tất cả mã nguồn `.puml` đã được định dạng chuẩn xác, bạn có thể render trực tiếp bằng extension PlantUML trong VS Code/IntelliJ hoặc dán vào PlantText / PlantUML Server để xem trước khi vẽ tay.*
