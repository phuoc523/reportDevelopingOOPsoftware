# Yêu cầu đồng bộ dữ liệu

**Dự án:** ScentStation  
**Task:** Task 1 – Define Data Synchronization Requirements  
**Trạng thái:** Tài liệu phân tích và đề xuất; chưa phải thiết kế đã triển khai

## 1. Tổng quan

Apache NiFi được xem xét như một thành phần hạ tầng để điều phối luồng dữ liệu: đọc dữ liệu từ nguồn được phê duyệt, kiểm tra và biến đổi theo hợp đồng, chuyển đến đích, đồng thời ghi nhận trạng thái để có thể giám sát và đối chiếu. NiFi không thay thế trách nhiệm nghiệp vụ của các Microservice và không được tự ý truy cập hoặc ghi vào dữ liệu thuộc quyền sở hữu của service khác.

Trong kiến trúc ScentStation, các service có cơ sở dữ liệu MySQL riêng và trao đổi qua các hợp đồng tích hợp. README gốc ghi NiFi là thành phần hạ tầng, nhưng cũng ghi rõ NiFi flow chưa được triển khai. Vì vậy, tài liệu này xác định yêu cầu và phạm vi đề xuất, không mô tả một luồng NiFi đang hoạt động.

Mục tiêu là xác định dữ liệu nào có nhu cầu tạo bản chiếu phục vụ báo cáo, tránh sao chép lại dữ liệu đã có cơ chế đồng bộ, duy trì khả năng nạp ban đầu và cập nhật tăng dần, đồng thời không phá vỡ ranh giới sở hữu dữ liệu của Microservices.

## 2. Hiện trạng hệ thống

### 2.1 Các database liên quan

Các tên database dưới đây được xác định từ cấu hình ứng dụng và Docker Compose trong repository. Các database là các nguồn/đích logic riêng; không có bằng chứng về một database dùng chung cho ba service.

| Database | Service sở hữu | Vai trò |
|---|---|---|
| `order_db` | Order Service | Lưu đơn hàng, các dòng hàng, thanh toán và dữ liệu liên quan đến callback, lệnh dispense và rà soát giao dịch. |
| `scentstation_catalog` | Catalog Service | Lưu dữ liệu sản phẩm và chiến dịch khuyến mãi. |
| `scentstation_reporting` | Reporting Service | Lưu bản sao giao dịch thanh toán dùng cho truy vấn và báo cáo. Dashboard cũng truy vấn một số service khác qua HTTP thay vì đọc database của các service đó. |

### 2.2 Schema nguồn và đích đã kiểm tra

#### Order – `order_db`

Schema được khai báo trong [schema.sql](../../../services/order-service/database/schema.sql). Các bảng và trường đáng chú ý:

| Bảng | Khóa/quan hệ được khai báo | Trường dữ liệu liên quan | Trường thời gian |
|---|---|---|---|
| `orders` | `id` là khóa chính | `tenant_id`, `customer_id`, `machine_id`, `slot_id`, `total_amount`, `currency`, `status`, `expires_at` | `created_at`, `updated_at` |
| `order_items` | `id` là khóa chính; `order_id` tham chiếu `orders.id` | `product_id`, `product_name`, `unit_price`, `quantity`, `line_total` | Không có trường thời gian trong schema |
| `payments` | `reference` là khóa chính; `order_id` là duy nhất và tham chiếu `orders.id` | `amount`, `currency`, `qr_payload`, `status` | `created_at`, `expires_at`, `paid_at`; không có `updated_at` |
| `payment_callback_events` | `event_id` là khóa chính | `reference`, `order_id`, `amount`, `currency`, `payment_status`, `outcome`, `error_status`, `message` | `received_at` |
| `dispensing_commands` | `command_id` là khóa chính; `order_id` là duy nhất và tham chiếu `orders.id`; `payment_reference` tham chiếu `payments.reference` | `machine_id`, `slot_id`, `status`, `manual_review_required`, `manual_review_reason` | `created_at`, `result_received_at` |
| `transaction_reviews` | `reference` là khóa chính; `order_id` có thể rỗng | `reason`, `status` | `created_at`, `resolved_at` |

`OrderItem.product_id` là một cột dữ liệu, nhưng schema trên không khai báo khóa ngoại tới Catalog. Order README cho biết Order lưu snapshot tên và giá sản phẩm từ yêu cầu tạo đơn, không gọi Catalog để lấy snapshot khi tạo đơn. Không được coi `product_id` là quan hệ khóa ngoại đã được database bảo đảm.

#### Catalog – `scentstation_catalog`

Schema migration được khai báo trong [V1__initial_catalog_schema.sql](../../../services/catalog-service/src/main/resources/db/migration/V1__initial_catalog_schema.sql).

| Bảng | Khóa/quan hệ được khai báo | Trường dữ liệu liên quan | Trường thời gian |
|---|---|---|---|
| `products` | `id` là khóa chính | `tenant_id`, `name`, `description`, `fragrance_notes`, `image_url`, `default_price`, `available` | `created_at`, `updated_at` |
| `campaigns` | `id` là khóa chính | `tenant_id`, `product_id`, `name`, `discount_type`, `discount_value`, `start_at`, `end_at`, `active` | `created_at`, `updated_at` |

`campaigns.product_id` biểu diễn mã sản phẩm trong dữ liệu nghiệp vụ; migration không khai báo khóa ngoại đến `products.id`. Schema không khai báo trường xóa mềm. `available` và `active` là trạng thái hiện có, không được mặc định rằng chúng thay thế đầy đủ sự kiện xóa.

Kiểu dữ liệu tenant không đồng nhất: `products.tenant_id` và `campaigns.tenant_id` là `BIGINT`, trong khi `orders.tenant_id` là `VARCHAR(100)`. Không thể ghép hai trường này nếu chưa có quy tắc ánh xạ tenant được xác nhận.

#### Reporting – `scentstation_reporting`

Schema trong [schema.sql](../../../services/reporting-service/src/main/resources/schema.sql) định nghĩa bảng `transactions`:

| Bảng | Khóa/quan hệ được khai báo | Trường dữ liệu liên quan | Trường thời gian |
|---|---|---|---|
| `transactions` | `id` là khóa chính; `reference` là duy nhất | `order_id`, `amount`, `currency`, `status` | `created_at`, `paid_at` |

Schema Reporting hiện được kiểm tra chỉ có bảng `transactions`; không có bảng sản phẩm hoặc chiến dịch. Không có `updated_at` trong `transactions`.

### 2.3 Luồng trao đổi dữ liệu đang có

- Order gửi thông tin thanh toán tới Reporting qua `POST /api/reporting/internal/transactions/sync` sau khi transaction của Order commit. Payload gồm `reference`, `orderId`, `amount`, `currency`, `status`, `createdAt` và `paidAt`.
- Reporting còn có endpoint đồng bộ lại theo `reference`; Reporting đọc khoản thanh toán từ Order qua HTTP rồi gọi cùng logic lưu bản ghi giao dịch.
- `reference` duy nhất giúp nhận diện bản ghi Reporting. Logic hiện tại hỗ trợ đồng bộ lặp lại có kiểm tra xung đột và cập nhật trạng thái theo quy tắc nghiệp vụ; đây là chức năng hiện hữu, không phải trách nhiệm cần NiFi sao chép.
- Nếu lời gọi Order → Reporting thất bại, thanh toán vẫn được commit tại Order. Tài liệu tích hợp xác nhận hiện chưa có outbox hoặc cơ chế tự động gửi lại; có thể yêu cầu đồng bộ lại từng reference qua API.
- Reporting đọc một số dữ liệu Machine và Operation qua HTTP. Tài liệu Reporting ghi nhận tích hợp Catalog đang bị giới hạn bởi hợp đồng xác thực/ngữ cảnh tenant chưa được thống nhất; chưa có cơ chế đồng bộ trực tiếp từ Catalog vào Reporting.
- Quy tắc sở hữu dữ liệu trong [README.md](../../../README.md) yêu cầu service không truy cập trực tiếp database của service khác và tích hợp liên service phải thông qua hợp đồng đã thống nhất.

## 3. Phạm vi đồng bộ dữ liệu đề xuất

### 3.1 Phạm vi ứng viên: Catalog sang bản chiếu Reporting

Đề xuất xem xét dữ liệu danh mục để phục vụ nhu cầu tra cứu/báo cáo trong tương lai, nhưng chỉ sau khi chủ sở hữu Catalog và Reporting thống nhất nhu cầu, hợp đồng dữ liệu, quyền truy cập và schema đích.

| Nguồn ứng viên | Dữ liệu nguồn | Đích dự kiến | Trạng thái |
|---|---|---|---|
| `scentstation_catalog` | `products`: dữ liệu định danh, tenant, tên, mô tả, ghi chú hương, ảnh, giá mặc định, trạng thái và thời điểm tạo/cập nhật | `scentstation_reporting`; bảng và cột đích: **Chưa xác định – cần xác nhận** | Đề xuất có điều kiện; chưa có bảng đích |
| `scentstation_catalog` | `campaigns`: dữ liệu định danh, tenant, mã sản phẩm, tên, loại và giá trị giảm, khoảng hiệu lực, trạng thái và thời điểm tạo/cập nhật | `scentstation_reporting`; bảng và cột đích: **Chưa xác định – cần xác nhận** | Đề xuất có điều kiện; chưa có bảng đích |

Không tạo bảng đích hoặc truy cập database Catalog trực tiếp trong phạm vi Task này. Theo quy tắc kiến trúc hiện tại, phương thức cấp dữ liệu cho pipeline (hợp đồng/export do service sở hữu hay ngoại lệ truy cập JDBC chỉ-đọc được phê duyệt) cũng cần được xác nhận trước khi coi phạm vi trên là được duyệt.

### 3.2 Dữ liệu loại khỏi phạm vi NiFi hiện tại

| Dữ liệu | Quyết định | Lý do |
|---|---|---|
| Order `payments` sang Reporting `transactions` | Không sao chép bằng NiFi trong luồng vận hành thông thường | Đã có đồng bộ sau commit và API đồng bộ lại theo reference. NiFi ghi trực tiếp cùng dữ liệu sẽ tạo cơ chế cạnh tranh, có thể bỏ qua xác thực và quy tắc idempotency của Reporting. |
| Order `orders`, `order_items` | Không đưa vào bản sao Reporting trong đề xuất hiện tại | Chưa có hợp đồng/bảng Reporting tương ứng; order item đã giữ snapshot sản phẩm theo Order README. Nhu cầu báo cáo cụ thể chưa được xác nhận. |
| `payment_callback_events`, `dispensing_commands`, `transaction_reviews` | Ngoài phạm vi | Schema và tài liệu hiện tại không xác định nhu cầu hoặc đích Reporting tương ứng. |
| Catalog `products`, `campaigns` trong môi trường chạy hiện tại | Chưa đồng bộ cho đến khi xác nhận điều kiện tại mục 3.1 | Reporting chưa có bảng đích; tích hợp Catalog hiện tại chưa có hợp đồng truy cập phù hợp được xác nhận. |

Nếu cần tự động khắc phục khoảng trống gửi thanh toán Order → Reporting, cần quyết định riêng về cách phát hiện giao dịch thiếu và cơ chế retry. Không được coi đó là phạm vi Catalog đề xuất hoặc triển khai một luồng NiFi ghi song song vào `transactions` khi chưa có thiết kế được duyệt.

## 4. Yêu cầu chức năng

Các yêu cầu sau áp dụng cho pipeline tương lai nếu phạm vi tại mục 3.1 được phê duyệt:

1. **Đọc dữ liệu:** đọc từ nguồn MySQL thông qua phương thức và tài khoản được chủ sở hữu dữ liệu phê duyệt; tài khoản nguồn chỉ có quyền tối thiểu cần thiết. Không đọc hoặc thay đổi dữ liệu ngoài phạm vi đã chốt.
2. **Truyền và xử lý:** tiếp nhận dữ liệu qua NiFi; kiểm tra schema, khóa, tenant, trường bắt buộc và kiểu dữ liệu; ghi nhận riêng bản ghi lỗi để điều tra, không âm thầm bỏ qua.
3. **Ghi dữ liệu đích:** chỉ ghi vào database/bảng đích đã được tạo và phê duyệt; không tự tạo schema, bảng hay cột. Cách ghi phải tuân theo hợp đồng của Reporting và không ghi vào bảng `transactions` bằng một luồng cạnh tranh với Order.
4. **Nạp ban đầu:** hỗ trợ nạp snapshot có kiểm soát, có thể chia lô; ghi nhận mốc bắt đầu/kết thúc, số bản ghi đọc, chấp nhận, từ chối và ghi thành công.
5. **Đồng bộ tăng dần:** dùng trường thay đổi và khóa ổn định đã được xác nhận. Với các bảng Catalog hiện có, `updated_at` là ứng viên watermark; thuật toán watermark, xử lý bản ghi cùng thời điểm và tải lại khoảng giao cần xác nhận trước khi vận hành.
6. **Xử lý lỗi:** phân loại lỗi kết nối, dữ liệu không hợp lệ, lỗi chuyển đổi và lỗi ghi; hỗ trợ retry có giới hạn cho lỗi tạm thời, giữ bản ghi lỗi để xử lý lại và cảnh báo khi vượt ngưỡng đã thống nhất. Không đánh dấu tiến độ thành công trước khi dữ liệu đích được xác nhận ghi.
7. **Idempotency:** chạy lại cùng batch hoặc cùng bản ghi không tạo thêm bản sao. Quy tắc insert/update phải dựa trên khóa đích được thống nhất và xử lý cạnh tranh khi cùng bản ghi được gửi nhiều lần.
8. **Đối soát:** cho phép đối chiếu số lượng, khóa và thời điểm đồng bộ giữa nguồn, đích và trạng thái pipeline trong một khoảng thời gian.

## 5. Yêu cầu phi chức năng

- **Nhất quán:** chấp nhận nhất quán cuối cùng giữa nguồn và bản chiếu; quy tắc xử lý bản ghi được cập nhật trong lúc nạp snapshot cần bảo đảm không bỏ sót thay đổi.
- **Khả năng mở rộng:** có thể tăng kích thước batch hoặc mức song song theo tải và khả năng MySQL; thông số cụ thể **Chưa xác định – cần xác nhận**.
- **Hiệu năng:** giới hạn tải đọc/ghi để không ảnh hưởng truy vấn và giao dịch của service; chu kỳ đồng bộ, độ trễ mục tiêu, khối lượng tối đa và thời gian hoàn tất **Chưa xác định – cần xác nhận**.
- **Giám sát và truy vết:** ghi nhận định danh flow/run, khoảng dữ liệu, số lượng theo trạng thái, thời điểm và lỗi. Không ghi password, API key, `qr_payload` hoặc nội dung nhạy cảm vào log.
- **Bảo mật:** lưu thông tin kết nối trong cơ chế quản lý secret được phê duyệt, không hard-code vào flow hoặc tài liệu; dùng kết nối mã hóa khi môi trường yêu cầu; tách quyền nguồn chỉ-đọc và đích chỉ-ghi theo bảng.
- **Cô lập dịch vụ:** pipeline không sở hữu dữ liệu nghiệp vụ, không cập nhật database nguồn và không làm thay đổi API hay luồng giao dịch của Microservices.
- **Khôi phục:** có thể khởi động lại từ checkpoint đã xác nhận; thời hạn lưu trạng thái, dữ liệu lỗi và log **Chưa xác định – cần xác nhận**.

## 6. Quy tắc đồng bộ dữ liệu

### 6.1 Khóa định danh và ghi lặp

- Các bảng Catalog có `id BIGINT` làm khóa chính. Nếu từng bảng có bảng đích riêng, `id` là ứng viên khóa upsert; nếu gom nhiều loại dữ liệu vào cùng một bảng thì cần thêm nguồn/loại bản ghi vào khóa.
- `campaigns.product_id` cần được giữ như mã tham chiếu nghiệp vụ. Do schema không khai báo khóa ngoại, pipeline không được mặc định mọi mã sản phẩm đều tồn tại hoặc tự suy ra ràng buộc DB.
- `tenant_id` phải được giữ nguyên và được kiểm tra theo hợp đồng. Không chuyển đổi hoặc ghép tenant Catalog `BIGINT` với Order `VARCHAR(100)` khi chưa có ánh xạ được phê duyệt.
- Quy tắc đích cho bản ghi đã tồn tại, xung đột giữa hai phiên bản và xử lý xóa **Chưa xác định – cần xác nhận**. Không được xóa cứng bản ghi đích chỉ vì nó vắng mặt trong một batch tăng dần.

### 6.2 Theo dõi thay đổi

- `products` và `campaigns` đều có `created_at` và `updated_at`; `updated_at` là ứng viên phát hiện insert/update. Cần xác minh việc cập nhật trường này trên mọi đường ghi và cách xử lý độ chính xác thời gian, múi giờ trước khi dùng làm watermark.
- Những bảng dưới đây **không có `updated_at`** theo schema đã kiểm tra:
  - Order: `order_items`, `payments`, `payment_callback_events`, `dispensing_commands`, `transaction_reviews`.
  - Reporting: `transactions`.
- `orders` có `updated_at`, nhưng không thay thế trường cập nhật của `payments`. Không dùng watermark của Order để suy ra mọi thay đổi thanh toán nếu chưa có bảo đảm nghiệp vụ và kiểm thử.
- Với bảng không có `updated_at`, phương án cần xem xét riêng gồm CDC/binlog được chủ sở hữu phê duyệt, sự kiện/outbox do service phát, đối soát theo khóa hoặc quét toàn bộ theo lịch. Chưa có bằng chứng cho thấy một phương án trong số này đã được thiết lập cho NiFi.
- `available` của sản phẩm và `active` của chiến dịch là các trạng thái hiện hữu; chưa xác định việc xóa vật lý, cách truyền tombstone, thời gian lưu bản ghi ngừng hoạt động hay việc một bản ghi bị xóa có cần loại khỏi Reporting.

### 6.3 Cập nhật, lỗi và khác biệt kiểu dữ liệu

- Bản ghi mới được tạo tại đích; bản ghi cũ được cập nhật theo quy tắc upsert và phiên bản/thời điểm nguồn sau khi hợp đồng đích được duyệt. Dữ liệu không hợp lệ phải được cách ly và báo cáo, không ghi một phần mà không có trạng thái rõ ràng.
- Không có transaction phân tán giữa hai database trong kiến trúc được kiểm tra. Vì vậy cần xử lý retry/idempotency và chỉ xác nhận checkpoint sau khi ghi đích thành công.
- Nếu sau này có yêu cầu Order → Reporting qua NiFi, cần xử lý rõ các khác biệt đã thấy trong schema: `payments.reference VARCHAR(64)` so với `transactions.reference VARCHAR(100)`; `payments.order_id BIGINT` so với `transactions.order_id VARCHAR(100)`; `DECIMAL(19,4)` so với `DECIMAL(15,2)`; `CHAR(3)` so với `VARCHAR(10)`; `DATETIME(6)` so với `DATETIME`. Reporting hiện chuẩn hóa thời gian về giây và kiểm tra precision amount trong luồng ứng dụng. Luồng NiFi không được tự làm tròn/cắt precision hoặc chuyển đổi trạng thái ngoài quy tắc đó.
- Trạng thái `available`, `active`, `discount_type` và trạng thái giao dịch là các trường nghiệp vụ; cần giữ giá trị/ý nghĩa theo nguồn và có quy tắc rõ khi gặp giá trị chưa biết.

## 7. Luồng đồng bộ dữ liệu dự kiến

Luồng dưới đây chỉ áp dụng cho phạm vi ứng viên Catalog → Reporting sau khi được duyệt và có hợp đồng truy cập cùng schema đích. Đây là mô tả yêu cầu, không khẳng định flow hay processor đã được cấu hình.

### 7.1 Nạp ban đầu

1. **Đọc dữ liệu:** lấy snapshot `products` và `campaigns` từ nguồn đã được cấp quyền; ghi nhận mốc snapshot.
2. **Xử lý dữ liệu:** chuyển đổi sang hợp đồng đích đã thống nhất, giữ nguyên khóa và tenant, chuẩn hóa thời gian/decimal theo quy tắc đã duyệt.
3. **Kiểm tra dữ liệu:** xác thực schema, khóa duy nhất, trường bắt buộc, tenant và tham chiếu `campaigns.product_id`; ghi bản ghi không hợp lệ vào vùng lỗi có truy vết.
4. **Ghi dữ liệu:** upsert vào bảng đích đã tồn tại theo khóa đã duyệt; không ghi vào `transactions`.
5. **Ghi nhận kết quả:** lưu checkpoint snapshot và số lượng đọc/ghi/lỗi; đối chiếu tổng số theo quy tắc đã thống nhất.

### 7.2 Đồng bộ tăng dần

1. **Đọc dữ liệu:** lấy các bản ghi có `updated_at` sau watermark đã lưu; cách chọn watermark và khoảng giao cần tránh bỏ sót các thay đổi cùng thời điểm.
2. **Xử lý dữ liệu:** chuẩn hóa dữ liệu theo cùng hợp đồng của nạp ban đầu, giữ định danh nguồn và thời điểm thay đổi.
3. **Kiểm tra dữ liệu:** kiểm tra schema, khóa, tenant và tính hợp lệ trước khi ghi; cách ly dữ liệu không hợp lệ.
4. **Ghi dữ liệu:** upsert các bản ghi hợp lệ; không thay đổi nguồn. Thao tác ghi phải idempotent để có thể chạy lại sau lỗi.
5. **Ghi nhận kết quả:** chỉ cập nhật watermark khi tất cả bản ghi trong phạm vi đã được ghi hoặc được ghi nhận rõ là lỗi cần xử lý; phát cảnh báo khi có lỗi chưa giải quyết.

Luồng Order → Reporting đang chạy tiếp tục do Order/Reporting quản lý qua HTTP theo hợp đồng hiện có. NiFi không được tạo luồng ghi song song vào bảng `transactions` nếu chưa có quyết định thay thế chính thức, kế hoạch chuyển đổi và tiêu chí đối soát.

## 8. Công nghệ sử dụng

| Công nghệ | Căn cứ và vai trò |
|---|---|
| Apache NiFi | Được README dự án nêu là thành phần hạ tầng; hiện chưa có flow NiFi trong `infra/nifi`. |
| MySQL | Database của các service; Docker Compose dùng ảnh MySQL `8.4`. |
| JDBC Driver | Các service Java khai báo driver `com.mysql.cj.jdbc.Driver`; chưa có cấu hình/driver NiFi trong repository. Driver và phiên bản cần dùng cho NiFi **Chưa xác định – cần xác nhận**. |
| Docker | Dự án chạy các service và MySQL bằng Docker Compose; chưa có dịch vụ NiFi trong Compose hiện tại. |
| Thành phần NiFi | Processor/controller service cụ thể **Chưa xác định – cần xác nhận** vì chưa có flow/template hoặc cấu hình NiFi trong repository. Chỉ chọn sau khi chốt phương thức truy cập, đích và yêu cầu vận hành. |

## 9. Kết quả mong đợi

- Có quyết định rõ ràng về phạm vi dữ liệu và chủ sở hữu hợp đồng nguồn/đích trước khi đồng bộ.
- Bản chiếu dữ liệu được phê duyệt (nếu có) phản ánh đúng bản ghi nguồn, không tạo trùng khi chạy lại và hỗ trợ nhận biết dữ liệu cập nhật.
- Luồng thanh toán Order → Reporting hiện hữu tiếp tục tuân theo API và quy tắc idempotency đang có; không bị đồng bộ trùng bởi NiFi.
- Có thể truy vết từng lần chạy, phát hiện bản ghi lỗi và đối chiếu kết quả theo chỉ số đã thống nhất.
- Việc đọc/ghi dữ liệu không ảnh hưởng đáng kể đến các Microservice hiện tại.

## 10. Những vấn đề cần xác nhận

1. Reporting có nhu cầu nghiệp vụ được duyệt để lưu bản chiếu `products` và/hoặc `campaigns` hay không; dữ liệu nào thực sự cần cho báo cáo?
2. Nếu đồng ý, schema đích trong `scentstation_reporting` gồm bảng/cột, khóa, unique constraint và quy tắc tenant nào? Hiện **Chưa xác định – cần xác nhận**.
3. NiFi được phép đọc dữ liệu qua hợp đồng/export do service sở hữu hay được duyệt ngoại lệ JDBC chỉ-đọc? Cách này phải phù hợp quy tắc sở hữu dữ liệu trong kiến trúc.
4. Với dữ liệu Catalog, đồng bộ đầy đủ trường nào; có cần lưu lịch sử giá/chiến dịch hay chỉ trạng thái hiện tại? Chính sách lưu trữ/lịch sử **Chưa xác định – cần xác nhận**.
5. Cách xử lý xóa vật lý, bản ghi ngừng hoạt động, thay đổi tenant, tham chiếu `campaigns.product_id` không hợp lệ và dữ liệu trùng khóa là gì?
6. Watermark, độ chính xác/múi giờ thời gian, overlap window, tần suất chạy, độ trễ mục tiêu, khối lượng, kích thước batch và mức song song là gì?
7. Chính sách retry, thời hạn lưu dữ liệu lỗi, dead-letter/quarantine, cảnh báo, phân quyền và đối soát nào được yêu cầu?
8. Có cần tự động bù các thanh toán chưa tới Reporting không? Nếu có, cách phát hiện thiếu, nguồn checkpoint và việc sử dụng API đồng bộ lại hiện hữu cần được thiết kế riêng; hiện chưa có outbox/retry tự động.
9. Nếu muốn thay thế luồng đồng bộ thanh toán hiện tại bằng NiFi, ai sở hữu thay đổi, cách bảo toàn xác thực/idempotency và kế hoạch chuyển đổi không gián đoạn là gì?
10. Driver JDBC, phiên bản NiFi, phiên bản MySQL được hỗ trợ và các processor/controller service được phép sử dụng là gì?

### Căn cứ trong repository

- [README kiến trúc dự án](../../../README.md)
- [Schema Order](../../../services/order-service/database/schema.sql)
- [Schema Catalog](../../../services/catalog-service/src/main/resources/db/migration/V1__initial_catalog_schema.sql)
- [Schema Reporting](../../../services/reporting-service/src/main/resources/schema.sql)
- [Cấu hình Docker Compose](../../../docker-compose.yml)
- [Tích hợp Reporting](../../../docs/reporting-integration.md)
- [README Order](../../../services/order-service/README.md)
- [README Catalog](../../../services/catalog-service/README.md)
- [README Reporting](../../../services/reporting-service/README.md)
