# PHÂN HỆ MACHINE & HARDWARE CONTROL SERVICE (:8083)

## 1. Giới thiệu phân hệ
`machine-service` là cầu nối trực tiếp giữa phần mềm và trạm máy vật lý Kiosk ngoài đời thực:
- Sở hữu cơ sở dữ liệu `machine_db`.
- Quản lý điểm đặt máy (`Location`), thực thể trạm máy (`Machine`), danh sách các khay cắm chai hương (`MachineSlot`).
- Kiểm soát trạng thái vận hành (`MachineOperationalStatus`: `OPERATIONAL`, `MAINTENANCE_REQUIRED`, `OFFLINE`, `DECOMMISSIONED`) và vòng đời thiết bị (`DeviceRegistrationStatus`).
- Tiếp nhận lệnh điều khiển phần cứng (`MachineCommand`), giao tiếp mạch điện tử bơm phun và xử lý các sự cố cảnh báo cảm biến (`MachineAlert`) kết hợp mẫu thiết kế Outbox Pattern (`AlertHandoffOutbox`) để bàn giao phiếu sửa chữa cho Operation Service.

## 2. Danh mục sơ đồ chi tiết
1. **`use-case.puml`**:
   - Quản trị trạm máy, vị trí, khay cắm, điều khiển bật/tắt vận hành, tiếp nhận cảnh báo cảm biến và thực thi lệnh phun nước hoa.
   - Thể hiện quan hệ `<<include>>` (Đăng ký máy bao gồm cấu hình Location) và `<<extend>>` (Cảnh báo lỗi CRITICAL mở rộng ra Alert Handoff).
2. **`class-diagram.puml`**:
   - Thực thể chính: `Machine`, `Location`, `MachineSlot`, `MachineAlert`, `MachineCommand`, `AlertHandoffOutbox`, `MachineHistory`.
   - Quan hệ: `Location` o-- `Machine` (Aggregation), `Machine` *-- `MachineSlot` (Composition), `Machine` *-- `MachineAlert` (Composition).
   - Tầng điều khiển & dịch vụ: `MachineController`, `SlotController`, `InternalOrderMachineController`, `DispenseCommandController`, `MachineService`, `SlotService`, `DispenseCommandService`, `MachineAlertService`.
3. **`sequence-diagram.puml`**:
   - Luồng thực thi lệnh phun nước hoa At-most-once: Kiểm tra trạng thái máy $\rightarrow$ Kiểm tra khay slot $\rightarrow$ Lưu command REQUESTED $\rightarrow$ Điều khiển phần cứng $\rightarrow$ Xử lý hai nhánh: Thành công (trừ dung tích, trả DISPENSED) hoặc Lỗi phần cứng (tạo cảnh báo, outbox bàn giao sang bảo trì, trả FAILED).
4. **`activity-diagram.puml`**:
   - Biểu đồ phân làn hoạt động: Xử lý Idempotency $\rightarrow$ Đọc cảm biến lưu lượng $\rightarrow$ Cập nhật dung tích $\rightarrow$ Cảnh báo sự cố $\rightarrow$ Khóa máy bảo trì.
