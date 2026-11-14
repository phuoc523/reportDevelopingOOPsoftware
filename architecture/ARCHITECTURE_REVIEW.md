# Báo cáo đối soát sơ đồ kiến trúc với mã nguồn

> **Cập nhật sau redesign (2026-11-14):** Các nhận xét bên dưới ghi lại trạng thái của bộ sơ đồ tại commit `2a7b130` trước khi sửa. Bộ 29 sơ đồ chính đã được thay thế và bổ sung 3 state diagram. Phiên bản authoritative hiện nằm trực tiếp trong `system-overview/`, `api-gateway/` và các thư mục service; `review-proposal/` chỉ còn là baseline so sánh lịch sử.

## Trạng thái bộ sơ đồ sau redesign

- 32 sơ đồ chính: 29 sơ đồ hiện hữu được thiết kế lại và 3 state diagram được bổ sung.
- Frontend/Kiosk chỉ là actor API; không được mô tả như component đã triển khai.
- Hardware/device transport, Catalog machine availability và Operation machine-slot integration được thể hiện bằng port mở rộng.
- Luồng dispensing hiện tại dừng ở `ACCEPTED` và polling; không giả định relay/sensor đã tồn tại.
- Tất cả sơ đồ đã qua `PlantUML -checkonly` và render PNG để kiểm tra bố cục.
- Quy ước và extension points được cập nhật tại `architecture/README.md`.

---

Ngày kiểm tra: 2026-11-14  
Phiên bản kiến trúc được kiểm tra: commit `2a7b130` (`update architecture`)  
Phạm vi: 29 file PlantUML, README kiến trúc, mã Java hiện tại, cấu hình Gateway và Docker Compose.

## 1. Kết luận

Bản kiến trúc mới đã sửa đúng một số lỗi quan trọng của phiên bản trước: port Order/Machine, tên database, nhiều field entity và một số endpoint nội bộ. Tuy nhiên, tuyên bố trong `architecture/README.md` rằng tài liệu đã “đối soát 100%” là chưa đúng.

Đánh giá hiện tại:

| Tiêu chí | Mức đánh giá | Nhận xét |
|---|---:|---|
| Khớp cấu hình deployment | 8/10 | Port và database đã được sửa; route Gateway vẫn liệt kê thiếu prefix |
| Khớp endpoint và integration | 6/10 | Sequence mới đúng hơn, nhưng use case/README/system overview còn endpoint cũ |
| Khớp class, field, enum | 6/10 | Catalog/Order cải thiện; Identity, Operation, Reporting còn nhiều field và enum sai |
| Khớp phương thức Java | 4/10 | Có nhiều chữ ký được “sáng tác” hoặc dùng tên cũ |
| Đúng ngữ nghĩa UML | 6/10 | Cú pháp hợp lệ; composition, dependency, include/extend còn dùng chưa chính xác |
| Khả năng dùng làm tài liệu as-is | 5/10 | Có thể dùng làm bản nháp nghiệp vụ, chưa nên coi là contract kỹ thuật |

Kết luận: **chưa khớp hoàn toàn với mã nguồn hiện tại**. Các sơ đồ nên được gắn nhãn `as-is`, `target` hoặc `conceptual` rõ ràng thay vì trộn cả ba loại trong cùng một hình.

## 2. Các lỗi mức Critical/High

### ARCH-01 — Đường dẫn introspection vẫn sai ở nhiều sơ đồ

- Sơ đồ dùng: `/internal/v1/identity/introspect`.
- Code dùng: `POST /internal/auth/introspect`.
- Nguồn: `InternalIntrospectionController` và các `IdentityIntrospectionClient`.
- Ảnh hưởng: `system-architecture.puml`, `core-flow-sequence.puml`, Identity use case, Identity sequence và README Identity.

### ARCH-02 — Core flow mô tả hardware dispense như đã triển khai hoàn chỉnh

`Machine Service` hiện chỉ tạo `MachineCommand` với trạng thái `ACCEPTED` và cung cấp API tra cứu. Không có controller/device adapter để ESP32/MQTT/serial ACK hoặc complete command. Vì vậy các bước sau chưa phải implementation as-is:

- Kích relay/pump trực tiếp.
- Nhận flow-sensor feedback.
- Trừ lượng hương trong Machine Service.
- Trả ngay `DISPENSED` cho Order Service.
- Tự sinh alert từ feedback phần cứng.

`MachineCommand.acknowledge()` và `complete()` tồn tại ở entity nhưng không có inbound adapter gọi chúng. `OrderService` có thể poll command, nhưng command sẽ không chuyển sang `DISPENSED/FAILED` bởi thiết bị trong code hiện tại.

### ARCH-03 — Operation vẽ một integration API không tồn tại

`MachineSlotClient` hiện chỉ có:

```java
MachineSlotInfo getSlot(Long tenantId, Long machineId, Long slotId);
```

Không có `updateSlotStatus()` hoặc `updateSlotCurrentBottle()`. Implementation `LocalMachineSlotClient` là stub cho profile local/test/docker-local và luôn trả slot active. Vì vậy các mũi tên “đồng bộ slot/bottle binding” và HTTP call thật sang Machine Service là sai với code hiện tại.

### ARCH-04 — Gateway trust-boundary mô tả sai header bị loại bỏ

Sơ đồ nói Gateway xóa `X-Actor-Id`, `X-Actor-TenantId`, `X-Actor-Grants`. Code không có prefix `x-actor-` trong `RESERVED`; code xóa các header cụ thể và prefix `x-verified-`, `x-internal-`, `x-authenticated-`.

Ngoài ra code luôn thay `X-Request-Id` bằng UUID mới, không “sinh hoặc bảo toàn” request ID cũ.

### ARCH-05 — Reporting class/sequence vẫn dựa trên API cũ

- `ReportPeriod` thực tế: `DAY`, `WEEK`, `MONTH`, `CUSTOM`.
- Sơ đồ: `DAILY`, `WEEKLY`, `MONTHLY`, `QUARTERLY`, `YEARLY`.
- Excel thực tế:
  - `GET /api/reporting/export/transactions`
  - `GET /api/reporting/export/revenue`
- Sơ đồ: `/api/reporting/excel/export`.
- `DashboardResponse` thực tế là record tổng hợp `transactions`, `machines`, `alerts`, `inventory`, `upstream`, `machineLocations`, `operations`, `access`; không phải sáu scalar tổng doanh thu/đơn/volume.

### ARCH-06 — README tuyên bố “khớp 100%” nhưng dữ liệu xác minh vẫn sai

Tuyên bố này nên đổi thành trạng thái kiểm tra có ngày/commit và danh sách known gaps. Nếu không, README tạo mức tin cậy cao hơn thực tế của tài liệu.

## 3. Đối soát từng file diagram

Ký hiệu:

- `OK`: phù hợp ở mức mục tiêu của loại sơ đồ.
- `PARTIAL`: đúng ý chính nhưng thiếu hoặc có chi tiết sai.
- `STALE`: còn dựa nhiều trên phiên bản cũ/target chưa triển khai.

| File | Trạng thái | Kết quả kiểm tra |
|---|---|---|
| `system-overview/system-architecture.puml` | PARTIAL | Port/DB đúng; introspection sai; Operation→Machine update không tồn tại; alert endpoint cũ; hardware adapter chưa triển khai |
| `system-overview/system-use-case.puml` | PARTIAL | Bao phủ actor tốt; refund/dispense và maintenance/alert dùng `extend` chưa rõ extension point; thiếu Inventory Staff/Viewer |
| `system-overview/core-flow-sequence.puml` | STALE | Endpoint Catalog/Machine/QR đã đúng; introspection sai; hardware response và trạng thái cuối chưa tồn tại trong code |
| `api-gateway/routing-architecture.puml` | PARTIAL | Luồng filter/route hợp lý; route prefix liệt kê thiếu; header sanitization và request-id sai |
| `api-gateway/trust-boundary-sequence.puml` | STALE | Mô tả xóa `X-Actor-*` không khớp code |
| `identity-service/use-case.puml` | PARTIAL | Chức năng chính đúng; endpoint introspection cũ; quan hệ include của assign permission không phản ánh API độc lập |
| `identity-service/class-diagram.puml` | STALE | Enum đã sửa; principal, DTO, JwtService, AccessPolicy, TenantService và relationship introspection vẫn sai |
| `identity-service/sequence-diagram.puml` | STALE | Endpoint cũ; query giả định join role/permission; JwtService API sai; response shape sai |
| `identity-service/activity-diagram.puml` | PARTIAL | Luồng login đúng ý; account inactive/locked trả 401 chứ không phải 403; JWT không chứa grants trực tiếp |
| `catalog-service/use-case.puml` | PARTIAL | Chức năng đúng; endpoint snapshot cũ; `CreateProduct include UpdateProduct` và `CalculatePrice include CreateCampaign` sai ngữ nghĩa |
| `catalog-service/class-diagram.puml` | PARTIAL | Entity/DTO cải thiện; nhiều method name sai; `ProductResponse` không phải record; Machine client signature sai |
| `catalog-service/sequence-diagram.puml` | OK/PARTIAL | Endpoint và flow gần đúng; nên thể hiện key verifier trả 403 theo code thay vì 401 |
| `catalog-service/activity-diagram.puml` | PARTIAL | Pricing đúng ý; field cũ `basePrice/active`; inactive product không nhất thiết được pricing service ném 409 như mô tả |
| `machine-service/use-case.puml` | STALE | Endpoint availability cũ; hardware execute/result được trình bày như đã hoàn chỉnh |
| `machine-service/class-diagram.puml` | STALE | Enum cải thiện; nhiều field và method sai; composition không có JPA object graph; thiếu AvailabilityService/security/jobs |
| `machine-service/sequence-diagram.puml` | STALE | API submit đúng; phần relay/sensor và terminal response chưa triển khai |
| `machine-service/activity-diagram.puml` | STALE | Toàn bộ nhánh hardware success/failure là target design |
| `order-service/use-case.puml` | PARTIAL | Bao phủ tốt; expire/review/include direction cần sửa; chưa thể hiện poll machine thực sự |
| `order-service/class-diagram.puml` | PARTIAL | Gần code hơn; vẫn có method/entity member sai và thiếu ACCEPTED/PROCESSING trong DispensingStatus |
| `order-service/sequence-diagram.puml` | PARTIAL | Callback chính xác hơn; phải mô tả Machine trả ACCEPTED và Order poll, không trả terminal ngay |
| `order-service/activity-diagram.puml` | STALE | Gộp create order và create QR; giả định terminal hardware response ngay |
| `operation-service/use-case.puml` | PARTIAL | Bao phủ nghiệp vụ tốt; restore machine chưa được code gọi; thiếu refill/adjustment/estimation/audit ở mức chi tiết |
| `operation-service/class-diagram.puml` | STALE | BottleMovement, enum, PostTest, MaintenanceStatus sai; client có method không tồn tại; thiếu nhiều module |
| `operation-service/sequence-diagram.puml` | PARTIAL | Close-ticket gần đúng; Machine client HTTP thật và slot update sai; payload tenantId type ghi sai |
| `operation-service/activity-diagram.puml` | PARTIAL | Close flow gần đúng; install flow nói cập nhật Machine nhưng code không làm |
| `reporting-service/use-case.puml` | STALE | Endpoint sync cũ; pull snapshots không phải extension của sync transaction |
| `reporting-service/class-diagram.puml` | STALE | ReportPeriod, DashboardResponse, client stereotype và hầu hết method service/controller sai |
| `reporting-service/sequence-diagram.puml` | PARTIAL | Sync transaction gần đúng; repository thực tế dùng lock method; Excel endpoint/service method sai |
| `reporting-service/activity-diagram.puml` | PARTIAL | Sync ý chính đúng; export permission và query shape cần cập nhật |

## 4. Phương thức trong class diagram không khớp Java

Danh sách dưới đây chỉ ghi các lỗi đáng kể; accessor do Lombok/record được đánh giá theo field thực tế.

### Identity

| Trong sơ đồ | Thực tế |
|---|---|
| `AccessPolicy.require()` | `has(String)`, `has(Permission)`, `platform()`, `tenant()`, `scope()` |
| `AccessPolicy.requireTenant()` | Không tồn tại |
| `AuthResponse.principal()` | Record gồm `token`, `tokenType`, `userId`, `username`, `role` |
| `RegisterRequest.tenantId()` | Không tồn tại; public register luôn tạo CUSTOMER |
| `JwtService.generate()` | `generateToken(User)` |
| `JwtService.parse()` | `extractUsername`, `extractRole`, `extractTokenVersion`; principal được filter dựng từ User |
| `TenantService.createTenant()` | `create()` |
| `TenantService.updateProfile()` | `platformUpdate()` và `brandUpdate()` |
| Introspection controller phụ thuộc `JwtService` | Thực tế phụ thuộc `AccessPolicy` và trả `current()` |

### Catalog

| Trong sơ đồ | Thực tế |
|---|---|
| `getProduct()` | `getProductById()` |
| `search()` | `searchProducts()` |
| `updateAvailability()` | `changeAvailability()` |
| `getActiveCampaigns()` | `getCampaigns()`, `getCampaignsByProduct()` |
| `calculateEffectivePrice()` | `getEffectivePrice()` |
| `getProductAvailabilityOnMachines()` | `getAvailability(tenantId, machineId, productId)` |
| `MachineAvailabilityClient.getAvailability(productId)` | `getProductAvailability(tenantId, machineId, productId)` |

`ProductResponse` và `EffectivePriceResponse` là Lombok class, không phải Java record. `OrderProductSnapshotResponse` mới là record.

### Machine

| Trong sơ đồ | Thực tế |
|---|---|
| `Machine.isOnline()`, `canDispense()` | Không tồn tại; availability nằm ở `AvailabilityService` |
| `MachineSlot.deduct()` | Không tồn tại |
| `SlotService.configureSlot()` | `create()` hoặc `update()` |
| `updateSlotStatus()` | `updateReportedStatus()` |
| `associateCartridge()` | Cập nhật qua `update()`; không có method riêng |
| `DispenseCommandService.submit()` | `create(CreateDispenseCommandRequest)` |
| `getResult()` | `get(commandId, tenantId)` |
| `MachineAlertService.raise()` | `record()` hoặc `recordLowStock()` |

Các field `MachineAlert.details/raisedAt` trong sơ đồ thực tế là `summary/firstSeenAt`; `MachineCommand.executedAt` thực tế tách `acknowledgedAt/completedAt`.

### Order

| Trong sơ đồ | Thực tế |
|---|---|
| `Order.isExpired()` | Expiration được xử lý trong service, không có method entity |
| `OrderItem.getSubtotal()` | Field/accessor là `lineTotal()` |
| `TransactionReview.markResolved()` | `resolve()` |
| `OrderController.create()` | `createOrder()` |
| `TransactionReviewController.list()` | `getReviews()` |

`DispensingStatus` thực tế có thêm `ACCEPTED` và `PROCESSING`; đây là hai trạng thái quan trọng của luồng async.

### Operation

| Trong sơ đồ | Thực tế |
|---|---|
| `Bottle.isAvailable()` | Không tồn tại; service kiểm tra enum status |
| `MachineSlotClient.updateSlotStatus()` | Không tồn tại |
| `MachineSlotClient.getSlot(String tenantId, ...)` | tenantId là `Long` |

Các enum trong sơ đồ sai:

- `BottleMovementType`: thực tế `INSTALL`, `REMOVE`, `REPLACE`, `REFILL`, `CUSTOMER_CONSUMPTION`, `DIAGNOSTIC_CONSUMPTION`, `DISPOSE`, `ADJUSTMENT`.
- `MaintenanceStatus`: thực tế có `WAITING_FOR_PARTS`, `READY_FOR_TEST`; không có `PENDING_PARTS`.
- `PostMaintenanceTestStatus`: thực tế `REQUESTED`, `RUNNING`, `PASSED`, `FAILED`.
- `MaintenanceSeverity`: thực tế `MINOR`, `MODERATE`, `MAJOR`, `CRITICAL`.

### Reporting

| Trong sơ đồ | Thực tế |
|---|---|
| `TransactionService.listTransactions()` | Query nằm ở `TransactionQueryService.search()` |
| `DashboardAggregationService.aggregateDashboard()` | `getDashboard(tenantId, period[, includePlatformTransactions])` |
| `ExcelExportService.exportToExcel()` | `exportTransactions(filter)` và `exportRevenue(start, end)` |
| `ExcelExportController.exportExcel()` | `exportTransactions()` và `exportRevenue()` |
| `MachineSnapshotClient.getMachineMetrics()` | Concrete class với `listMachines`, `listLocations`, `listAlerts` |
| `OperationInventoryClient.getInventorySnapshots()` | Concrete class với các list API cụ thể |

`OrderPaymentClient`, `MachineSnapshotClient`, `OperationInventoryClient` là concrete class, không phải interface.

## 5. Nhận xét về tiêu chuẩn trình bày diagram

### Điểm đạt

- Tất cả 29 file `.puml` pass `PlantUML -checkonly`.
- Font, theme, title và màu service tương đối thống nhất.
- Sequence diagram dùng `alt`, activation và response arrow đúng cú pháp.
- Có phân chia system overview và từng bounded context.

### Điểm cần sửa

1. **Không trộn domain model và code inventory quá dày trong một class diagram.** Nên tách:
   - Domain/entity diagram.
   - Application/component diagram.
   - Integration/security diagram.
2. **Chỉ dùng composition khi có ownership/lifecycle thật.** Nhiều entity chỉ lưu scalar ID, không có JPA association/cascade; dùng dependency `..>` hoặc association chú thích `ID reference` phù hợp hơn.
3. **Không đặt multiplicity lên dependency nếu muốn mô tả cardinality dữ liệu.** Dùng association line hoặc note về logical reference.
4. **Use case `include/extend` phải thể hiện hành vi bắt buộc/tùy chọn**, không dùng để biểu diễn thứ tự thời gian hoặc prerequisite dữ liệu.
5. **Sequence as-is không được vẽ bước chưa có adapter.** Nếu cần giữ target flow, dùng màu/note `<<planned>>`.
6. **Không ghi SQL giả lập nếu không bám schema chính xác.** Có thể dùng `save()`/`find...()` để tài liệu ít bị stale.
7. **Cần legend và scope ngay trong mỗi hình**, đặc biệt phân biệt sync REST, local stub, optional configured integration và planned hardware.
8. **Cần version metadata:** commit code, ngày kiểm tra, người duyệt hoặc script generate.

## 6. Thiếu sót về kiến trúc/implementation được diagram làm lộ ra

1. Chưa có device inbound API hoặc adapter MQTT/serial để ACK/complete `MachineCommand`.
2. Operation chưa có production `MachineSlotClient`; profile docker-local vẫn dùng stub.
3. Catalog `MachineAvailabilityClient` cũng chỉ có local/test stub fail-closed; chưa có adapter production trong source hiện tại.
4. Alert handoff phụ thuộc URL cấu hình; Docker Compose hiện không cấu hình maintenance URL, nên không nên vẽ như luôn hoạt động.
5. Gateway chưa loại bỏ tổng quát prefix `X-Actor-*` dù tài liệu nói có.
6. Reporting không lưu tenant/machine/product trong `Transaction`; các báo cáo tenant-scoped dựa chủ yếu vào authorization và upstream snapshot, cần được mô tả rõ.
7. Thiếu state diagram riêng cho Order/Payment/Dispensing, MachineCommand, MachineAlert, Bottle và MaintenanceTicket.

## 7. Bản đề xuất để review

Thư mục `architecture/review-proposal/` chứa một baseline `as-is` không ghi đè bản hiện tại:

- System architecture đã phân biệt implemented, optional và missing adapter.
- Core flow thể hiện `ACCEPTED` rồi poll thay vì giả định trả `DISPENSED` ngay.
- Trust-boundary bám đúng code hiện tại và đánh dấu khoảng trống `X-Actor-*`.
- Sáu class diagram service chỉ dùng field/method chính có thật và ghi note phần bị lược bỏ.

Sau khi review, có thể chọn một trong hai hướng:

1. Thay thế dần sơ đồ hiện tại bằng bản proposal; hoặc
2. Giữ sơ đồ hiện tại làm `target architecture`, đổi tên/label rõ ràng và bổ sung bộ `as-is` song song.
