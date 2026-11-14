# PHÂN HỆ OPERATION SERVICE (:8085)

## 1. Giới thiệu phân hệ
`operation-service` phụ trách quản lý toàn diện công tác hậu cần kho hương và quy trình bảo dưỡng kỹ thuật hiện trường:
- Sở hữu cơ sở dữ liệu `operation_db`.
- **Miền Kho hàng (Inventory Domain)**:
  - Quản lý lô sản xuất (`InventoryBatch`), chai nước hoa (`Bottle`), trạng thái chai (`BottleStatus`), và lịch sử luân chuyển kho (`BottleMovement`).
  - Quản lý lắp đặt/thay thế chai vào trạm máy Kiosk (`BottleInstallation`), kiểm tra ràng buộc chặt chẽ với cấu hình khay trên `machine-service`.
  - Theo dõi mức tiêu hao tia xịt (`DispenseConsumption`) và đối soát thể tích thực tế (`SensorReconciliation`).
- **Miền Bảo trì (Maintenance Domain)**:
  - Tiếp nhận cảnh báo khẩn và phát hành phiếu bảo dưỡng (`MaintenanceTicket`).
  - Quản lý danh mục kiểm tra tiêu chuẩn (`MaintenanceChecklistItem`), nhật ký công việc & vật tư thay thế (`MaintenanceRepairLog`).
  - Kiểm định nghiệm thu sau sửa chữa (`PostMaintenanceTest`) với điều kiện bắt buộc `PASSED` trước khi được phép khôi phục máy Kiosk sang trạng thái phục vụ.

## 2. Danh mục sơ đồ chi tiết
1. **`use-case.puml`**:
   - Thể hiện các ca sử dụng nhập kho theo lô, dán mã chai, lắp đặt vào khay máy, phân công kỹ thuật viên, thực hiện checklist, kiểm thử nghiệm thu và khôi phục máy.
   - Quan hệ `<<include>>` (Đóng ticket bắt buộc hoàn tất checklist, repair log và post-test) và `<<extend>>` (Tự động mở ticket khi có cảnh báo khẩn).
2. **`class-diagram.puml`**:
   - Thực thể chính: `InventoryBatch`, `Bottle`, `BottleInstallation`, `BottleMovement`, `MaintenanceTicket`, `MaintenanceChecklistItem`, `MaintenanceRepairLog`, `PostMaintenanceTest`.
   - Quan hệ: `InventoryBatch` *-- `Bottle` (Composition), `MaintenanceTicket` *-- `MaintenanceChecklistItem` (Composition), `MaintenanceTicket` *-- `PostMaintenanceTest` (Composition).
   - Tầng dịch vụ & tích hợp: `BottleInstallationService`, `MaintenanceTicketService`, `MachineSlotClient`.
3. **`sequence-diagram.puml`**:
   - Thể hiện 2 luồng thao tác: (1) Kỹ thuật viên lắp chai vào máy Kiosk (đối soát khớp loại hương và kích hoạt slot), (2) Quy trình thẩm định điều kiện để đóng phiếu bảo trì.
4. **`activity-diagram.puml`**:
   - Biểu đồ phân làn kết hợp cả vòng lặp kiểm tra bảo trì (vòng lặp repeat-while Post-test PASSED) và luồng lắp đặt chai an toàn.
