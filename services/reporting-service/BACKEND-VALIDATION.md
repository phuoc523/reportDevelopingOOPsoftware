# Tổng hợp kiểm thử backend Reporting

Ngày kiểm tra: 2026-11-13. Branch: `test-2026-11-13`.

Phạm vi: Reporting backend và các API upstream. Không cần Admin Web; không thay
đổi source/config Admin Web, Gateway, Compose hoặc service của member khác trong
lượt chốt này. Các thay đổi chưa commit có sẵn trong workspace được giữ nguyên.

## Build và kiểm thử mặc định

```powershell
mvn -f .\services\reporting-service\pom.xml clean verify
```

Kết quả: **178 PASS, 0 FAIL/error, 7 bài tích hợp tùy chọn được bỏ qua** khi chưa
bật biến môi trường. Maven tạo executable JAR:

`target/reporting-service-0.0.1-SNAPSHOT.jar`

Các nhóm được kiểm tra:

| Nhóm | Nội dung |
|---|---|
| Đồng bộ giao dịch | PENDING, PAID, trùng reference, trạng thái cũ, dữ liệu xung đột, timestamp, payload sai |
| HTTP client | JSON hợp lệ/hỏng/rỗng, sai tenant, HTTP error, kết nối lỗi và read timeout thật với server kiểm soát |
| Machine | Trạng thái máy, vị trí trống/mất metadata, nguồn lỗi, trạng thái chưa biết |
| Operation | Tách customer/diagnostic, loại adjustment/dispose, command trùng/xung đột, ngày UTC/Vietnam, bảo trì/quá hạn |
| Security | Bearer, quyền report/export/platform, tenant/scope, header giả, URL mã hóa, Identity lỗi, API key nội bộ |
| API dashboard | Cấu trúc JSON hiện có và metadata quyền `access` bổ sung |
| Báo cáo | Ngày không hợp lệ, khoảng ngày bao gồm ngày cuối, tỷ lệ khi không có giao dịch |
| Excel | Mở lại XLSX bằng POI, dữ liệu qua nhiều trang, currency từng giao dịch, ô chuỗi và doanh thu |

## Lượt tích hợp thật

Các test tùy chọn dùng database MySQL riêng `reporting_phase2_test` và các URL
service trên network Docker local. Dữ liệu mẫu được tạo qua API hoặc qua Reporting
service/repository trong database test; không reset hay xóa dữ liệu hiện có.

Lệnh chọn các bài tích hợp sau khi cấu hình môi trường:

```powershell
mvn -f .\services\reporting-service\pom.xml test '-Dtest=TransactionSyncMySqlTest,MachineReportingIntegrationTest,OperationReportingIntegrationTest,IdentityReportingIntegrationTest,ReportingBackendHttpIntegrationTest' '-Dspring.test.context.cache.maxSize=1'
```

Thiết lập `REPORTING_MYSQL_TEST_URL`, `REPORTING_MYSQL_TEST_USERNAME`,
`REPORTING_MYSQL_TEST_PASSWORD`, `ORDER_SERVICE_URL`, `MACHINE_SERVICE_URL`,
`OPERATION_SERVICE_URL`, `IDENTITY_SERVICE_URL`. Không ghi credentials thực tế vào repo.
Khi chạy trong Docker, hostname service phải truy cập được từ network của test runner;
không dùng `localhost` để trỏ sang một container khác.

| Test | Dữ liệu/service thực tế | Giới hạn |
|---|---|---|
| TransactionSyncMySqlTest (3 bài) | MySQL 8.4 và Order API; tạo đồng thời, cập nhật đồng thời, đọc payment PENDING→PAID và replay | Không kiểm tra push tự động vào container Reporting gốc |
| MachineReportingIntegrationTest (1 bài) | Machine API/MySQL, tạo máy/vị trí mẫu, đọc alerts/availability/history và tổng hợp | Transaction và Operation được mock; không kiểm tra thiết bị vật lý |
| OperationReportingIntegrationTest (1 bài) | Operation API/MySQL, batch/bottle/installation, customer/diagnostic/failed command, bảo trì | Adapter slot `docker-local` của Operation là stub; không chứng minh hardware dispense |
| IdentityReportingIntegrationTest (1 bài) | Identity API, CUSTOMER/JWT thật, chống header giả, logout/thu hồi token; production security chain với request probe | Không tự cấp platform/tenant role; quyền nâng cao vẫn là unit/security test |
| ReportingBackendHttpIntegrationTest (1 bài) | Reporting/Tomcat trên cổng ngẫu nhiên, MySQL thật, Identity thật | Không đi qua Gateway; không có account platform thật để đọc tài chính thành công |

Kết quả Docker tổng hợp: **7 PASS, 0 FAIL/error, 0 SKIP**. Cụ thể:

| Lượt chạy | PASS | FAIL/error | SKIP |
|---|---:|---:|---:|
| `clean verify` mặc định | 178 | 0 | 7 bài opt-in |
| Docker với 5 lớp tích hợp được bật | 7 | 0 | 0 |
| Tổng hợp các trường hợp kiểm thử | **185** | **0** | **0 chưa chạy** |

Các XML trong `target/surefire-reports` sau hai lượt ghi nhận 185 test đạt. Trên
MySQL thật đã quan sát được deadlock trong tranh chấp tạo reference lần đầu; test
xác nhận giao dịch thua có thể replay an toàn, chỉ còn một bản ghi. Đây là tình
huống được xử lý, không phải test thất bại.

Reporting HTTP thật chạy được trên Tomcat cổng ngẫu nhiên và xác nhận API key,
PENDING→PAID, idempotency, từ chối trạng thái cũ, một hàng MySQL, Bearer bắt buộc,
CUSTOMER bị từ chối tài chính và token sau logout trả 401. Không coi kết quả này
là kiểm chứng route Gateway hoặc quyền tài chính thành công với platform account thật.

## Hợp đồng dashboard cho client backend

Endpoint không đổi: `GET /api/reporting/dashboard?tenantId=12&period=day`.

Metadata mới do server tính từ profile Identity đã xác minh:

```json
{
  "access": {
    "scope": "tenant",
    "tenantId": "12",
    "canReadFinancialReports": false,
    "canExport": false
  }
}
```

Đây là thông tin cho client hiển thị, không thay thế kiểm tra quyền tại server.
Với tài khoản tenant, `transactions=null`; Reporting không truy vấn giao dịch toàn
hệ thống. Với platform, metadata quyền tài chính/export tương ứng quyền được xác minh.
Kiểm tra `upstream` cho từng nguồn trước khi hiểu số 0 là dữ liệu xác nhận.

`GET /api/reporting/health` là liveness của Reporting, không chứng minh mọi upstream
đã sẵn sàng. API nội bộ vẫn dùng `X-Internal-Api-Key`, độc lập Bearer của người dùng.

## Kiểm tra thủ công không cần giao diện

- Import [Postman collection](postman/ReportingBackend.postman_collection.json).
- Đọc [hướng dẫn URL, tài khoản, quyền và thứ tự chạy](postman/README.md).
- Collection có 16 request và credential/token/key mặc định rỗng; đã kiểm tra cú
  pháp JSON. Collection chưa được chạy tự động bằng Postman/Newman; các API được
  kiểm thử tự động bằng JUnit/RestClient ở các lớp nêu trên.

## Những điểm chưa thể coi là hoàn tất toàn hệ thống

1. Gateway và container Reporting gốc chưa được xác nhận chạy xuyên suốt. Snapshot
   Docker trong lượt này: Gateway/Reporting ở trạng thái Created, Catalog Exited;
   các bài tích hợp dùng service đang chạy và Reporting HTTP test trên cổng ngẫu nhiên.
2. Compose chưa truyền `IDENTITY_SERVICE_URL` vào Reporting. Muốn chạy container đó
   cần thống nhất cấu hình với nhóm; có thể chạy JAR với biến môi trường phù hợp.
3. Catalog cần hợp đồng xác thực S2S/Gateway đáng tin cậy trước khi bổ sung tên/brand.
4. Transaction schema chưa có tenantId nên tài chính/export hiện giới hạn platform.
   Cần tài khoản platform/tenant thật để kiểm thử quyền nâng cao ngoài mock.
5. Quyền Reporting đang là projection từ source RolePermissions của Identity; cần
   API quyền hiệu lực để giảm nguy cơ lệch policy khi Identity thay đổi.
6. Order chưa có outbox/retry bền vững; replay thủ công khôi phục từng reference.
7. Operation chưa có consumption listing để đếm thất bại/TIMEOUT/UNCERTAIN. Endpoint
   movement chưa phân trang/lọc thời gian; Reporting hiện lọc trong bộ nhớ.
8. Tổng doanh thu hiện chưa nhóm/chuyển đổi currency. Order hiện cung cấp VND, nhưng
   Reporting nhận được currency khác qua API; không dùng tổng tiền này cho tập dữ
   liệu trộn nhiều tiền tệ. Excel giao dịch giữ currency ở từng dòng.
9. Refund hiện là quy ước toàn bộ amount của giao dịch REFUNDED, chưa có partial refund.
10. Chưa kiểm thử Admin Web, phần cứng xịt hoặc triển khai production. Lượt này chỉ
    chốt phạm vi backend; không tự commit hoặc push.
