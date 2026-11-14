# Proposed as-is architecture diagrams

> **Historical comparison only.** Bộ proposal này được tạo trước lượt redesign toàn diện. Bộ sơ đồ authoritative để review hiện nằm trực tiếp trong `architecture/system-overview`, `architecture/api-gateway` và sáu thư mục service. Không tiếp tục cập nhật implementation contract từ các file trong thư mục này.

Bộ sơ đồ này là baseline đề xuất để review, được dựng từ mã nguồn tại commit `2a7b130` ngày 2026-11-14.

Nguyên tắc:

- Chỉ vẽ luồng đã có implementation.
- Thành phần chưa có adapter được ghi `<<missing adapter>>` hoặc `<<local stub>>`.
- Không khẳng định hardware flow đã hoàn chỉnh.
- Class diagram tập trung vào public API và domain state quan trọng, không cố liệt kê toàn bộ getter/setter.
- Scalar ID giữa các aggregate/service dùng dependency hoặc note `ID reference`, không dùng composition giả.

Danh sách:

```text
review-proposal/
├── README.md
├── system-overview/
│   ├── system-architecture.puml
│   └── core-flow-sequence.puml
├── api-gateway/
│   └── trust-boundary-sequence.puml
├── identity-service/class-diagram.puml
├── catalog-service/class-diagram.puml
├── machine-service/class-diagram.puml
├── order-service/class-diagram.puml
├── operation-service/class-diagram.puml
└── reporting-service/class-diagram.puml
```
