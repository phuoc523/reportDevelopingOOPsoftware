# PHÂN HỆ REPORTING & ANALYTICS SERVICE (:8086)

## 1. Giới thiệu phân hệ
`reporting-service` đóng vai trò là trung tâm tổng hợp, khai phá dữ liệu và trực quan hóa hiệu quả kinh doanh của toàn nền tảng ScentStation:
- Sở hữu cơ sở dữ liệu `reporting_db`.
- Tiếp nhận đồng bộ sự kiện giao dịch từ `order-service` qua `/internal/v1/reporting/sync/transactions`.
- Thu thập số liệu vận hành từ `machine-service` (thời gian hoạt động, cảnh báo hỏng hóc) và `operation-service` (tiêu hao chai hương kho bãi).
- Cung cấp API phục vụ Dashboard trực quan (doanh thu, số lượt xịt, tỷ lệ hoàn tất) và xuất báo cáo tài chính/vận hành định dạng Excel (`.xlsx`).

## 2. Danh mục sơ đồ chi tiết
1. **`use-case.puml`**:
   - Thể hiện các ca sử dụng đồng bộ giao dịch, kiểm tra tính hợp lệ dữ liệu, tổng hợp dashboard theo chu kỳ (`ReportPeriod`), báo cáo doanh thu theo máy Kiosk, và kết xuất file Excel.
   - Quan hệ `<<include>>` (Đồng bộ bắt buộc validate dữ liệu; Xuất Excel bao gồm truy vấn doanh thu).
2. **`class-diagram.puml`**:
   - Thực thể chính: `Transaction`, `ReportPeriod`.
   - DTO: `DashboardResponse`, `TransactionSummaryResponse`, `TransactionSyncRequest`.
   - Tầng điều khiển & dịch vụ: `TransactionSyncController`, `DashboardController`, `ExcelExportController`, `TransactionService`, `DashboardAggregationService`, `ExcelExportService`, `OperationReportService`.
   - Các Client tích hợp ngoại vi: `OrderPaymentClient`, `MachineSnapshotClient`, `OperationInventoryClient`.
3. **`sequence-diagram.puml`**:
   - Thể hiện 2 luồng: (1) Nhận đồng bộ giao dịch từ Order Service (đối soát khóa nội bộ và kiểm tra idempotency), (2) Brand Admin xuất file báo cáo Excel động.
4. **`activity-diagram.puml`**:
   - Biểu đồ phân làn thể hiện quy trình thẩm định dữ liệu đầu vào khi đồng bộ và quy trình xuất báo cáo bảng tính.
