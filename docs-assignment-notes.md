# SCENTSTATION — DANH SÁCH TASK TRIỂN KHAI THEO 5 THÀNH VIÊN

> **Nguồn yêu cầu:** `đề tài xây dựng OOP (1).docx` (28 trang), phần **Functional Requirements (1–17)**, **Non-Functional Requirements**, **Expected Deliverables** và **Proposed Tasks**.  
> **Mục tiêu:** Chia công việc cho 5 thành viên theo **Service lớn → Task → Task con**, bao phủ đặc tả và phân rõ trách nhiệm tích hợp.  
## I. Nguyên tắc chung

1. **Mỗi task có một người chịu trách nhiệm chính.** Người khác phối hợp qua API contract, review và kiểm thử; không phải viết thay toàn bộ module.
2. **Bảo đảm các quy tắc quan trọng:** tách dữ liệu theo Tenant; chỉ backend xác thực thanh toán; mỗi đơn hợp lệ có **không quá một lần xịt thành công**; không tự động xịt lại khi kết quả không chắc chắn; trừ tồn kho khi đã xác nhận xịt thành công.
3. **Thiết kế theo hợp đồng trước khi code:** xác định ID (`tenantId`, `machineId`, `slotId`, `orderId`, `paymentReference`, `commandId`, `cartridgeId`), trạng thái, API, mã lỗi và thông điệp MQTT giữa các service.
4. **Tất cả service đều có:** phân quyền đúng Tenant, xác thực nơi cần thiết, logging/audit sự kiện quan trọng, dữ liệu mẫu, test nghiệp vụ, README chạy riêng và hướng dẫn tích hợp.
5. **Hỗ trợ chéo trong giới hạn khoảng 10% thời gian của mỗi người:** ưu tiên review PR, thống nhất API, ghép end-to-end và gỡ lỗi liên service. Nếu phần cứng hoặc tích hợp vượt mức này, nhóm phải phân bổ lại sau khi ước lượng công sức, không mặc định giao cho người 4 hoặc người 5.
6. **Không cắt yêu cầu đầu ra của DOCX một cách âm thầm.** Tài liệu yêu cầu **prototype kiosk ít nhất 4 slot độc lập**. Device Simulator hỗ trợ phát triển/test, **không mặc nhiên thay thế prototype vật lý**; mọi phương án thay thế cần được giảng viên chấp thuận.

### Phân công đầu mối

| Người | Phạm vi sở hữu | Thư mục phụ trách chính | Branch |
|---|---|---|---|
| **1** | Identity, Multi-Tenant, API Gateway, nền tảng Audit/Notification | `services/identity-service/`, `services/api-gateway/`, `shared/` (quy ước chung) | `feature/identity-platform` |
| **2** | Catalog, Inventory, Refill, Maintenance Ticket/Checklist | `services/catalog-service/`, `services/operation-service/` | `feature/catalog-inventory` |
| **3** | Order, QR Payment, Reconciliation, Refund/Manual Review | `services/order-service/` | `feature/order-payment` |
| **4** | Machine, Slot, IoT/MQTT, Alert, Device/Prototype | `services/machine-service/`, `device/` | `feature/machine-iot` |
| **5** | Customer Kiosk UI, Admin Portal, Reporting | `frontend/customer-web/`, `frontend/admin-web/`, `services/reporting-service/` | `feature/frontend-reporting` |

> **Ranh giới cần nhớ:** Người 2 xử lý **nghiệp vụ kho và phiếu bảo trì** trong `operation-service`; người 4 xử lý **máy, telemetry, cảnh báo nguồn thiết bị và chế độ bảo trì**. Người 5 chỉ phát triển giao diện/Reporting; backend nào lỗi thì người sở hữu backend đó sửa.

---

## II. NGƯỜI 1 — IDENTITY & PLATFORM

**Mục tiêu:** Nền tảng có nhiều thương hiệu sử dụng chung hệ thống nhưng cô lập dữ liệu; mọi người dùng và API tuân thủ phân quyền, có thể truy vết thao tác quan trọng.

- **Service lớn: `services/identity-service/` — Tenant, User, Authentication & Authorization**
  - **Task P1-01 — Quản lý Tenant/Brand ở cấp nền tảng**
    - Tạo, cập nhật, kích hoạt, tạm ngưng thương hiệu; lưu định danh `tenantId` duy nhất.
    - Lưu trạng thái Tenant, thông tin cấu hình cấp thương hiệu; chặn truy cập nghiệp vụ khi Tenant bị tạm ngưng theo chính sách.
    - Phân biệt quyền **Platform Super Administrator** (toàn nền tảng) với **Brand Administrator** (chỉ Tenant mình phụ trách).
    - Cung cấp API danh sách/chi tiết Tenant theo đúng quyền.
  - **Task P1-02 — Quản lý tài khoản và vòng đời tài khoản**
    - Tạo tài khoản Brand Administrator và nhân sự thuộc Tenant; cập nhật thông tin và gán phạm vi trách nhiệm.
    - Kích hoạt, vô hiệu hóa, khóa, mở khóa, đặt lại thông tin đăng nhập theo quyền cho phép.
    - Thu hồi phiên đăng nhập/quyền truy cập khi tài khoản bị vô hiệu hóa hoặc phát hiện truy cập đáng ngờ.
    - Lưu lịch sử thay đổi tài khoản và người thực hiện.
  - **Task P1-03 — Đăng nhập, đăng xuất, quản lý phiên và bảo vệ tài khoản**
    - Xây dựng quy trình đăng nhập/đăng xuất, cấp và kiểm tra phiên/token, hết hạn và thu hồi phiên.
    - Bảo vệ mật khẩu, xử lý sai thông tin đăng nhập, ghi nhận lần đăng nhập thành công/thất bại.
    - Trả về thông tin Tenant, vai trò và phạm vi có hiệu lực cho client sử dụng.
    - Không giả định khách dùng kiosk phải tạo tài khoản; khách có thể thao tác theo luồng kiosk công khai có kiểm soát.
  - **Task P1-04 — RBAC đa vai trò và quyền thao tác nhạy cảm**
    - Khai báo các vai trò: **Platform Super Administrator, Brand Administrator, Operations Manager, Technician, Inventory Staff, Viewer** và quyền truy cập kiosk/Customer phù hợp.
    - Gán nhiều vai trò cho một tài khoản nếu được cho phép; kiểm tra quyền trên từng API.
    - Thiết kế các quyền bổ sung cho **refund, điều chỉnh tồn kho, diagnostic spray, cấu hình máy**.
    - Cho phép quản lý/thu hồi quyền bởi quản trị viên có thẩm quyền.
  - **Task P1-05 — Cô lập dữ liệu Multi-Tenant và phạm vi vận hành**
    - Chuẩn hóa `tenantId` trong xác thực, các request nội bộ và dữ liệu liên quan.
    - Kiểm tra truy cập theo Tenant; chặn người dùng thương hiệu A đọc/sửa dữ liệu của thương hiệu B.
    - Hỗ trợ giới hạn quyền theo địa điểm, máy hoặc trách nhiệm, khi dữ liệu quyền có cấu hình tương ứng.
    - Viết test truy cập trái Tenant cho sản phẩm, máy, đơn, tồn kho và báo cáo cùng các chủ service.
  - **Task P1-06 — Cấu hình nền tảng và chính sách chung**
    - Cung cấp cấu hình/quản lý các tham số hệ thống thiết yếu, chính sách thiết bị và kênh thanh toán ở cấp nền tảng.
    - Thiết lập mẫu thông báo và tham số thời gian chờ ở dạng có thể cấu hình theo phân quyền.
    - Phân biệt cấu hình cấp platform và cấp tenant, giới hạn quyền sửa.

- **Service lớn: `services/api-gateway/` — API Gateway & API Security**
  - **Task P1-07 — Gateway, định tuyến và CORS**
    - Thiết lập route vào Identity, Catalog, Operation, Order, Machine và Reporting Service.
    - Chuẩn hóa prefix/version API và CORS phù hợp cho kiosk/admin.
    - Trả lỗi nhất quán cho service không khả dụng, request không hợp lệ và không đủ quyền.
  - **Task P1-08 — Hợp đồng bảo mật và giao tiếp giữa các service**
    - Thống nhất cách truyền danh tính, `tenantId`, correlation/request ID và quyền đã xác minh.
    - Quy định không tin `tenantId` do frontend tự khai nếu không trùng phạm vi đã xác thực.
    - Thiết lập quy tắc bảo vệ endpoint nội bộ, TLS và bảo mật cấu hình/secret ở mức triển khai nhóm.

- **Service lớn: `shared/` hoặc module nền tảng — Audit & Notification Contracts**
  - **Task P1-09 — Audit Log dùng chung**
    - Định nghĩa bản ghi: actor, Tenant, action, target object, timestamp, source, kết quả và dữ liệu trước/sau khi phù hợp.
    - Cung cấp hàm/giao thức ghi audit dùng bởi các service; lưu dữ liệu không cho tài khoản thông thường chỉnh sửa/xóa.
    - Tiếp nhận sự kiện đăng nhập, thay quyền, đổi giá, đổi slot, nạp hàng, refund, lệnh xịt và trạng thái bảo trì từ người sở hữu module.
    - Cung cấp API tra cứu audit theo thời gian, người dùng, máy, hành động và mức độ được phân quyền.
  - **Task P1-10 — Cơ chế phân phối thông báo**
    - Quản lý template/thông báo trong hệ thống và kênh ngoài được cấu hình trong phạm vi demo.
    - Nhận yêu cầu thông báo từ Machine Alert, Inventory và Order; gửi đến đúng người/Tenant/phạm vi.
    - Ghi trạng thái gửi, tránh xử lý trùng cùng một sự kiện thông báo.
    - **Ranh giới:** Người 4 phát hiện/quản lý **Alert**; người 1 chỉ phụ trách **kênh phân phối thông báo**.
  - **Task P1-11 — Hợp đồng chung, kiểm thử và bàn giao nền tảng**
    - Công bố quy ước JSON/error/status, quy tắc Tenant, quyền và API authentication để các service tích hợp.
    - Test đăng nhập, thu hồi quyền, phân quyền và truy cập chéo Tenant; cung cấp tài khoản mẫu theo role.
    - Bàn giao README, biến môi trường, dữ liệu khởi tạo và hướng dẫn chạy riêng Identity/Gateway.
    - Phối hợp ghép Docker Compose qua cấu hình Gateway, **không chịu trách nhiệm tự sửa mọi service**.

**Bàn giao của người 1:** Identity + Gateway chạy độc lập; Tenant và Role APIs; quy ước bảo mật; Audit/Notification Contracts; test Multi-Tenant/RBAC; hướng dẫn chạy và tài khoản mẫu.

---

## III. NGƯỜI 2 — CATALOG, INVENTORY & MAINTENANCE OPERATIONS

**Mục tiêu:** Quản lý danh mục nước hoa và toàn bộ vòng đời chai/lõi, tồn kho theo slot, quy trình nạp hàng và hồ sơ bảo trì. Các tác vụ điều khiển thiết bị vật lý do người 4 đảm trách.

- **Service lớn: `services/catalog-service/` — Product, Brand Content & Pricing**
  - **Task P2-01 — Thông tin hiển thị của thương hiệu và sản phẩm**
    - Quản lý thông tin/logo/mô tả/liên hệ và nội dung trình chiếu của thương hiệu thuộc Tenant.
    - Tạo/sửa/vô hiệu hóa sản phẩm nước hoa: tên, mô tả, nốt hương, hình ảnh, giá mỗi lần xịt và trạng thái.
    - API danh sách/chi tiết/tìm kiếm và dữ liệu cho kiosk, portal quản trị, báo cáo.
    - Đảm bảo dữ liệu sản phẩm thuộc đúng Tenant.
  - **Task P2-02 — Giá bán, tình trạng bán và chiến dịch**
    - Quản lý giá gốc, giá được cấu hình theo slot (phối hợp người 4 quản lý slot), tình trạng bán.
    - Hỗ trợ chương trình khuyến mãi, trải nghiệm miễn phí và giá tạm thời theo phạm vi đã thống nhất.
    - Ghi lịch sử thay đổi giá vào Audit Log; trả giá hiện hành cho Order Service.
    - Không sửa giá đã được lưu cố định trong order trước đó.
  - **Task P2-03 — Tích hợp Catalog với Machine và Frontend**
    - Cung cấp API lấy nước hoa có thể chọn theo `machineId`/`slotId`, phối hợp xác nhận trạng thái với người 4.
    - Đưa ra response thống nhất có sản phẩm, nốt hương, ảnh, giá, trạng thái và Tenant.
    - Chuẩn bị dữ liệu mẫu nhiều thương hiệu, nhiều mùi hương, nhiều trạng thái sản phẩm.

- **Service lớn: `services/operation-service/` — Inventory, Bottle/Cartridge & Refill**
  - **Task P2-04 — Quản lý lô hàng (Inventory Batch)**
    - Tạo/quản lý lô nhập: sản phẩm, mã lô, số lượng, ngày nhận, hạn sử dụng và Tenant.
    - Cung cấp danh sách tồn theo lô/sản phẩm và tra cứu lịch sử nhập.
    - Kiểm tra điều kiện lô quá hạn, thiếu thông tin hoặc không thuộc Tenant.
  - **Task P2-05 — Quản lý chai/lõi nước hoa (Bottle/Cartridge)**
    - Đăng ký mã chai/lõi duy nhất, liên kết sản phẩm/lô và lượng ban đầu.
    - Quản lý trạng thái: available, installed, nearly-empty, empty, damaged, expired.
    - Chặn sử dụng chai/lõi hỏng, hết hạn hoặc đang lắp ở slot khác.
  - **Task P2-06 — Lắp, tháo, thay chai/lõi và Refill**
    - Gắn đúng chai/lõi vào đúng máy/slot; bảo đảm mỗi slot có tối đa một chai/lõi đang hoạt động.
    - Lưu người thao tác, thời gian, máy, slot, chai cũ/mới và lượng đo trước/sau nạp.
    - Ghi lịch sử installation, refill, removal, replacement và disposal.
    - Phối hợp người 4 để đồng bộ assignment và khóa slot nếu không an toàn.
  - **Task P2-07 — Ước lượng số lần xịt còn lại và trừ tồn kho**
    - Lưu lượng ban đầu, mức tiêu thụ đã hiệu chuẩn cho mỗi lần xịt và số lần xịt thành công.
    - Chỉ cập nhật tồn kho bán hàng sau khi có **kết quả xịt thành công xác nhận từ máy**.
    - Ghi nhận diagnostic spray ở luồng riêng, không nhập nhằng với bán hàng.
    - Tính lượng còn lại/số lần xịt ước tính; khóa khả dụng của slot nếu không đủ cho một lần xịt an toàn.
  - **Task P2-08 — Đối chiếu cảm biến và phát hiện tiêu thụ bất thường**
    - Nhận trọng lượng/mức đo từ Machine Service; so sánh với lượng dự đoán.
    - Ghi chênh lệch và dấu hiệu rò rỉ/tiêu thụ bất thường để tạo cảnh báo phối hợp người 4.
    - Cập nhật ngưỡng low-stock và cung cấp thông tin phục vụ báo cáo refill.
  - **Task P2-09 — Điều chỉnh tồn kho và lịch sử dịch chuyển**
    - Cho Inventory Staff có quyền điều chỉnh tồn kho, yêu cầu ghi **lý do bắt buộc**.
    - Ghi actor, Tenant, thời gian, giá trị trước/sau; gửi Audit Event.
    - API xem lịch sử nạp, nhập/xuất, hiệu chỉnh, chai đã tháo và hủy.

- **Service lớn: `services/operation-service/` — Maintenance Ticket & Technician Workflow**
  - **Task P2-10 — Phiếu bảo trì và phân công kỹ thuật viên**
    - Tạo maintenance ticket thủ công hoặc nhận cảnh báo quan trọng từ người 4 để tạo tự động.
    - Lưu máy, Tenant, địa điểm, loại lỗi, độ nghiêm trọng, ưu tiên, trạng thái và hạn xử lý.
    - Phân công Technician, theo dõi xác nhận, lịch sử xử lý và thời gian hoàn thành.
    - Phát tín hiệu **critical maintenance** cho người 4/người 3 để chặn giao dịch mới khi cần.
  - **Task P2-11 — Checklist kiểm tra, sửa chữa và đóng phiếu**
    - Lưu checklist kiểm tra, chẩn đoán, sửa chữa, hỗ trợ refill và test sau bảo trì.
    - Ghi kết quả kiểm tra, hành động khắc phục, linh kiện thay thế, chi phí, ghi chú/hình ảnh hỗ trợ.
    - Chỉ cho đóng ticket khi có kết quả sửa chữa; chỉ cho phép quy trình mở lại máy sau checklist và **post-maintenance test** thành công từ người 4.
    - Cung cấp lịch sử bảo trì theo máy/linh kiện và dữ liệu uptime/downtime/tần suất bảo trì cho Reporting.
  - **Task P2-12 — Test, tài liệu và dữ liệu mẫu cho Catalog/Operation**
    - Test một slot không có hai chai active; không lắp chai sai mùi/hết hạn; không điều chỉnh kho thiếu lý do.
    - Test trừ tồn kho đúng một lần theo kết quả xịt xác nhận; timeout/duplicate không làm trừ hai lần.
    - Test ticket/checklist và không đưa máy trở lại hoạt động khi chưa đủ điều kiện.
    - Bàn giao API docs, dữ liệu mẫu, README chạy service và cách phát sinh sự kiện cho các module liên quan.

**Bàn giao của người 2:** Catalog API; Batch/Bottle/Cartridge/Refill APIs; tồn kho ước tính và lịch sử; Maintenance Ticket/Checklist APIs; test nghiệp vụ kho/bảo trì; tài liệu tích hợp.

---

## IV. NGƯỜI 3 — ORDER, PAYMENT & TRANSACTION RECONCILIATION

**Mục tiêu:** Tạo đơn đúng trạng thái máy/slot, thu tiền an toàn, chỉ yêu cầu xịt sau thanh toán đã xác minh, lưu vết đủ để đối soát và xử lý sự cố.

- **Service lớn: `services/order-service/` — Order Lifecycle**
  - **Task P3-01 — Kiểm tra điều kiện và tạo Order**
    - Chỉ cho tạo đơn khi máy online, sẵn sàng, không bảo trì và slot khả dụng; phối hợp người 2 và 4 kiểm tra sản phẩm/tồn kho.
    - Lưu snapshot ngay lúc tạo: `tenantId`, `machineId`, `slotId`, `productId`, giá, tiền tệ, thời gian tạo/hết hạn.
    - Không lấy giá mới để sửa order đã tạo.
    - Chặn tạo đơn khi máy không giao tiếp được với backend.
  - **Task P3-02 — Trạng thái Order và lịch sử chuyển trạng thái**
    - Thiết kế luồng chờ thanh toán, thành công, thất bại, bị hủy, quá hạn và các trạng thái xịt sau thanh toán.
    - Kiểm soát chuyển trạng thái hợp lệ; ghi lại thời điểm, nguồn sự kiện và lý do thay đổi.
    - Cung cấp API chi tiết/trạng thái order cho kiosk và portal.
  - **Task P3-03 — Payment Reference và mã QR động**
    - Sinh payment reference riêng cho từng order và dữ liệu QR phù hợp nhà cung cấp/mô phỏng.
    - Gắn số tiền, tiền tệ, thời gian hết hạn, trạng thái chờ thanh toán.
    - Cung cấp API lấy QR và kiểm tra trạng thái thanh toán.

- **Service lớn: `services/order-service/` — Payment Verification & Idempotency**
  - **Task P3-04 — Nhận và kiểm tra Payment Webhook/Callback**
    - Tiếp nhận thông báo thanh toán từ payment provider hoặc bộ giả lập có hợp đồng tương đương.
    - Xác minh nguồn/chữ ký webhook theo phương thức đã chọn, mã tham chiếu, số tiền, tiền tệ và trạng thái.
    - Chỉ backend đánh dấu đã thanh toán; không tin ảnh chụp hoặc trạng thái do kiosk tự báo.
    - Lưu nội dung/sự kiện xử lý quan trọng cho đối soát và Audit.
  - **Task P3-05 — Chống webhook trùng và hết hạn đơn**
    - Định danh sự kiện để webhook trùng không cập nhật order hoặc phát lệnh xịt nhiều lần.
    - Tự chuyển đơn chưa thanh toán sang expired sau thời gian cấu hình.
    - Không tạo lệnh xịt với đơn failed/cancelled/expired hoặc sự kiện thanh toán không hợp lệ.
    - Xử lý trường hợp thông báo thanh toán đến muộn bằng quy trình đối soát/review, không tự xịt bù.

- **Service lớn: `services/order-service/` — Dispensing Orchestration**
  - **Task P3-06 — Yêu cầu xịt và chống phát lệnh lặp**
    - Sau khi xác minh thanh toán thành công, tạo yêu cầu thực hiện đơn gửi sang Machine Service theo contract.
    - Gắn `orderId`, `paymentReference`, `commandId`/idempotency key, máy, slot và thời gian hiệu lực.
    - Đảm bảo một order không sinh nhiều dispensing command đang hoạt động.
    - **Ranh giới:** Người 3 điều phối nghiệp vụ Order; người 4 tạo/kiểm tra lệnh thiết bị và thực thi IoT.
  - **Task P3-07 — Đồng bộ kết quả xịt với trạng thái Order**
    - Nhận trạng thái nhận lệnh, thực hiện, thành công, thất bại, timeout/không xác định từ người 4.
    - Chỉ đánh dấu Order là `DISPENSED` sau khi máy báo **xịt thành công**.
    - Gửi sự kiện kết quả xịt thành công đến người 2 để trừ tồn kho đúng một lần.
    - Khi kết quả không chắc chắn: chuyển manual review, **không tự động tạo lệnh xịt thứ hai**.

- **Service lớn: `services/order-service/` — Reconciliation, Review & Refund**
  - **Task P3-08 — Quy trình xử lý ngoại lệ sau thanh toán**
    - Xác định giao dịch đã thanh toán nhưng xịt thất bại/chưa xác nhận.
    - Tạo hồ sơ manual review, lưu nguyên nhân, bằng chứng từ máy và trạng thái xử lý.
    - Hỗ trợ refund/authorized retry theo chính sách và quyền bổ sung; mặc định không retry trường hợp kết quả xịt không chắc chắn.
    - Gửi thông tin trạng thái và hướng dẫn khôi phục cho kiosk/portal.
  - **Task P3-09 — Đối soát thanh toán và giao dịch**
    - Đối chiếu dữ liệu order/payment nội bộ với bản ghi payment provider hoặc nguồn giao dịch mô phỏng được thống nhất.
    - Phát hiện thanh toán thiếu, thừa, lệch số tiền, callback trễ hoặc giao dịch chưa được xử lý.
    - Lưu kết quả đối soát và trạng thái cần can thiệp của Operations Manager.
  - **Task P3-10 — Tra cứu giao dịch, Audit và dữ liệu báo cáo**
    - Cung cấp lọc giao dịch theo thời gian, máy, địa điểm, nước hoa, payment reference, trạng thái và Tenant.
    - Phát sự kiện audit cho thanh toán, refund, thay trạng thái Order và xử lý đối soát.
    - Cung cấp dữ liệu số đơn, doanh thu, tỷ lệ thanh toán/xịt thành công và sự cố cho Reporting.
  - **Task P3-11 — Test thanh toán, vòng đời đơn và bàn giao**
    - Test webhook hợp lệ/sai chữ ký/sai tiền/trùng/thanh toán trễ.
    - Test máy offline, slot rỗng, đơn expired và không phát lệnh xịt trong các tình huống cấm.
    - Test thanh toán thành công nhưng xịt thất bại/không rõ kết quả; bảo đảm không xịt lại tự động.
    - Bàn giao sơ đồ trạng thái Order/Payment, API docs, dữ liệu mẫu và README chạy service.

**Bàn giao của người 3:** Order API; QR Payment và xác thực callback; trạng thái đơn; luồng gửi yêu cầu xịt; Reconciliation/Review/Refund tối thiểu; bộ test an toàn/idempotency; hướng dẫn tích hợp.

---

## V. NGƯỜI 4 — MACHINE, IOT, DEVICE & ALERTS

**Mục tiêu:** Máy/slot có định danh và trạng thái rõ ràng, điều khiển xịt qua kênh IoT an toàn, có phản hồi xác nhận, phát hiện lỗi/cảnh báo, hỗ trợ mô phỏng và prototype thực tế.

- **Service lớn: `services/machine-service/` — Machine, Location & Slot Management**
  - **Task P4-01 — Đăng ký thiết bị, Tenant và địa điểm**
    - Quản lý machine serial number, device identity duy nhất, trạng thái kích hoạt/thu hồi.
    - Gán một máy cho đúng một Tenant tại một thời điểm và một địa điểm vận hành.
    - Quản lý địa điểm và lịch sử lắp đặt, di chuyển, thay đổi cấu hình/quyền sở hữu máy.
  - **Task P4-02 — Cấu hình Slot và trạng thái khả dụng**
    - Quản lý số lượng/ID slot, sản phẩm đang lắp, trạng thái enabled/disabled, ngưỡng tồn kho và liều lượng/thời gian xịt được hiệu chuẩn.
    - Nhận thông tin chai/lõi đang lắp từ Operation Service; từ chối slot không hợp lệ hoặc chứa sai sản phẩm.
    - Phối hợp người 2 về giá cấu hình theo slot và ngưỡng tồn kho.
    - Lưu lịch sử thay đổi cấu hình và gửi Audit Event.
  - **Task P4-03 — Trạng thái vận hành và điều khiển từ xa**
    - Quản lý online/offline/unstable, disabled, maintenance, error, hoạt động của từng slot và thời gian kết nối cuối.
    - Hỗ trợ enable/disable máy hoặc slot bởi người có quyền; cấu hình giờ hoạt động.
    - Cung cấp API kiểm tra khả dụng cho Order và Catalog.
    - Chặn order mới nếu máy offline, có lỗi nghiêm trọng hoặc đang bảo trì.

- **Service lớn: `services/machine-service/` — Secure IoT Messaging & Telemetry**
  - **Task P4-04 — MQTT và xác thực thiết bị**
    - Thiết kế topic/message contract cho heartbeat, telemetry, command, ACK, result và device event.
    - Xác minh định danh thiết bị; từ chối thiết bị chưa đăng ký/bị thu hồi/sai định danh.
    - Thiết lập kênh giao tiếp an toàn (TLS/chứng thực theo khả năng broker và thiết bị của nhóm).
    - Liên kết mọi message với machine/command ID và Tenant chính xác.
  - **Task P4-05 — Heartbeat, telemetry và lịch sử sự kiện**
    - Nhận heartbeat; xác định online, unstable, offline theo mốc thời gian cấu hình.
    - Ghi telemetry: trọng lượng/mức nước hoa, trạng thái cửa, rò rỉ, actuator và nguồn điện.
    - Ghi device events: khởi động/tắt máy, reconnect, maintenance mode, lỗi sensor và lỗi xịt.
    - Theo dõi firmware/configuration version, lưu sự kiện quan trọng và dữ liệu lịch sử có thể tổng hợp cho Reporting.
  - **Task P4-06 — Quản lý Alert từ máy và phối hợp thông báo/bảo trì**
    - Sinh cảnh báo offline, slot trống, lỗi cảm biến/cơ cấu xịt, cửa mở, rò rỉ, xịt thất bại; nhận tín hiệu low-stock từ người 2.
    - Gắn severity, status, Tenant, máy, địa điểm, thời gian; gộp cảnh báo cùng sự cố chưa xử lý.
    - Cho Operations Manager xác nhận, phân công, xử lý, mở lại; theo dõi thời gian phản hồi và escalate khi quá hạn.
    - Gửi sự kiện cần thông báo cho người 1 và alert nghiêm trọng để người 2 tạo Maintenance Ticket.

- **Service lớn: `services/machine-service/` — Secure Dispensing & Safety**
  - **Task P4-07 — Tạo và theo dõi lệnh xịt an toàn**
    - Nhận yêu cầu đã thanh toán từ Order Service; xác minh quyền, `orderId`, máy, slot, trạng thái máy.
    - Gán ID duy nhất, thời điểm tạo/hết hạn và chữ ký bảo mật cho command.
    - Yêu cầu thiết bị phản hồi ACK trước khi thực hiện; lưu ACK, thời gian thực thi, kết quả, dữ liệu sensor và failure code.
    - Loại bỏ command sai địa chỉ, hết hạn, không hợp lệ, trùng hoặc không tương ứng đơn đã xác thực.
  - **Task P4-08 — Khóa liên động an toàn và chống xịt lặp**
    - Không xịt khi cửa mở, maintenance mode, slot rỗng, máy mất kết nối hoặc lỗi phần cứng nghiêm trọng.
    - Chỉ điều khiển slot được chỉ định trong **một chu kỳ xịt theo liều lượng đã hiệu chuẩn**.
    - Thiết bị lưu ID lệnh đã xử lý để chống thực thi lại nếu broker gửi trùng/thiết bị reconnect.
    - Với timeout/kết quả không xác định, lưu để đối soát, **không tự động xịt lại**.
  - **Task P4-09 — Chế độ bảo trì và chẩn đoán thiết bị**
    - Cho người có quyền đưa máy vào maintenance mode, khóa đơn mới và đọc lỗi/telemetry/chẩn đoán.
    - Hỗ trợ test spray có kiểm soát và ghi nhận riêng với giao dịch khách hàng.
    - Phản hồi kết quả post-maintenance test cho người 2; không cho chuyển về active khi chưa đủ checklist + test.

- **Service lớn: `device/` — Device Simulator, Firmware & Kiosk Prototype**
  - **Task P4-10 — Device Simulator và đồng bộ sau mất mạng**
    - Giả lập máy tối thiểu 4 slot, sensor/door/actuator, heartbeat, trạng thái còn hàng và một lần xịt.
    - Mô phỏng ACK/result, thất bại, timeout, lỗi sensor, máy offline/reconnect và command trùng.
    - Tạm lưu sự kiện khi mất kết nối; gửi bù sau reconnect mà không tạo record/lệnh xịt trùng.
    - Viết hướng dẫn chạy simulator và kịch bản demo luồng máy.
  - **Task P4-11 — Prototype vật lý ít nhất 4 slot theo DOCX**
    - Xây dựng/integrate nguyên mẫu kiosk có **ít nhất 4 ngăn nước hoa điều khiển độc lập**.
    - Tích hợp firmware, điều khiển cơ cấu xịt, cảm biến/trạng thái cửa và giới hạn thời gian/liều lượng xịt an toàn.
    - Kiểm thử từng slot độc lập, hiệu chuẩn một chu kỳ xịt, cơ chế dừng khi lỗi và liên thông với MQTT.
    - Nếu nhóm chỉ có điều kiện làm simulator, phải **xin duyệt thay đổi đầu ra** thay vì ghi là hoàn thành prototype thật.
  - **Task P4-12 — Test IoT, tài liệu giao thức và bàn giao**
    - Test ACK/result, command trùng/hết hạn/sai máy, offline/reconnect, lỗi cảm biến, cửa mở và maintenance.
    - Test một lần xịt duy nhất dù request trùng; test critical alert và điều kiện mở lại máy.
    - Bàn giao tài liệu MQTT topics/payload, API machine/alert, sơ đồ giao tiếp, firmware/simulator, README và kết quả demo 4 slot.

**Bàn giao của người 4:** Machine/Slot API; MQTT + xác thực thiết bị; Telemetry/Alert API; chống xịt lặp và an toàn; Device Simulator; prototype 4 slot theo đặc tả; test lỗi IoT.

---

## VI. NGƯỜI 5 — CUSTOMER KIOSK, ADMIN PORTAL & REPORTING

**Mục tiêu:** Tạo trải nghiệm kiosk chọn nước hoa → thanh toán QR → xịt, portal cho từng vai trò vận hành và dashboard/báo cáo lấy dữ liệu từ các API của người 1–4.

- **Service lớn: `frontend/customer-web/` — Touch-screen Customer Kiosk**
  - **Task P5-01 — Màn hình kiosk và danh mục nước hoa theo máy**
    - Xây dựng giao diện ưu tiên cảm ứng, hiển thị sản phẩm có sẵn ở máy hiện tại.
    - Hiển thị thương hiệu, tên, ảnh, nốt hương, mô tả, giá mỗi lần xịt và hướng dẫn sử dụng.
    - Chỉ cho chọn một mùi khả dụng theo machine/slot; hiển thị rõ trạng thái không phục vụ.
  - **Task P5-02 — Chọn sản phẩm và xác nhận Order**
    - Cho khách xác nhận đúng mùi/giá trước khi gọi API tạo order.
    - Hiển thị lỗi khi máy mất kết nối, slot không khả dụng hoặc lượng nước hoa không đủ.
    - Không triển khai giỏ hàng nhiều sản phẩm như tính năng bắt buộc; đặc tả mô tả **một phiên trải nghiệm với một mùi**.
  - **Task P5-03 — QR Payment và hiển thị trạng thái thanh toán**
    - Hiển thị QR sinh động từ Order Service, payment reference/thời hạn phù hợp.
    - Cập nhật trạng thái waiting, successful, failed, cancelled, expired với độ trễ thấp.
    - Không có nút tự xác nhận đã thanh toán; chỉ hiển thị kết quả backend cung cấp.
  - **Task P5-04 — Hướng dẫn xịt, trạng thái xịt và phục hồi sự cố**
    - Hướng dẫn vị trí đặt giấy thử/cổ tay và trạng thái processing/success/failed.
    - Phân biệt thanh toán thành công nhưng xịt thất bại hoặc chưa xác nhận; hiển thị hướng dẫn xử lý.
    - Tự quay về màn hình chính sau giao dịch hoặc thời gian không hoạt động.
  - **Task P5-05 — QR thông tin sản phẩm và nội dung bổ sung**
    - Hiển thị QR để khách xem thông tin nước hoa, ưu đãi hoặc kênh mua sắm bên ngoài kiosk.
    - Tích hợp hiển thị nội dung phù hợp chiến dịch đang áp dụng từ Catalog Service.

- **Service lớn: `frontend/admin-web/` — Platform, Brand & Operations Portal**
  - **Task P5-06 — Giao diện đăng nhập, Tenant và phân quyền**
    - Xây dựng login/logout, phiên và menu theo quyền/tư cách người dùng.
    - Màn hình Platform Admin: Tenant, Brand Admin, thống kê/quyền cấp platform theo API.
    - Màn hình Brand Admin: nhân sự/role, thông tin thương hiệu; không hiển thị dữ liệu Tenant khác.
  - **Task P5-07 — Giao diện Catalog, Campaign và Inventory**
    - Giao diện thêm/sửa/vô hiệu hóa sản phẩm, hình ảnh, nốt hương, giá và chiến dịch.
    - Giao diện batch, bottle/cartridge, lắp/tháo/refill, tồn kho ước tính, ngưỡng low-stock, lịch sử.
    - Màn hình điều chỉnh tồn kho yêu cầu lý do và chỉ hiện cho người có quyền.
  - **Task P5-08 — Giao diện Machine, Alert, Maintenance và Technician**
    - Màn hình máy/địa điểm/slot, trạng thái online/offline, maintenance, firmware, telemetry và cấu hình.
    - Màn hình danh sách/chi tiết cảnh báo, xác nhận, phân công, xử lý và mở lại.
    - Màn hình maintenance ticket, checklist, nhật ký sửa chữa và xác nhận post-maintenance test.
    - Hiển thị nút enable/disable hoặc diagnostic spray theo quyền được backend cấp.
  - **Task P5-09 — Giao diện giao dịch, đối soát và xử lý lỗi**
    - Danh sách/chi tiết Order & Payment, lọc trạng thái và xem luồng xịt.
    - Màn hình payment success nhưng dispensing failed/uncertain, manual review và refund theo quyền.
    - Hiển thị dấu vết order → payment → command → result → inventory/audit ở mức dữ liệu các API cung cấp.

- **Service lớn: `services/reporting-service/` — Dashboard, Report & Export**
  - **Task P5-10 — API tổng hợp báo cáo có phân quyền**
    - Lấy/tổng hợp dữ liệu từ Identity, Catalog, Operation, Order và Machine qua API/event contract được bàn giao.
    - Các số liệu: thương hiệu/máy, online/offline/maintenance/error; giao dịch/doanh thu; kết quả xịt; sự cố thanh toán-xịt; nước hoa phổ biến.
    - Các số liệu: tồn kho ước tính, slot low-stock, nhu cầu refill; uptime/downtime, alert frequency, thời gian xử lý sự cố.
    - Kiểm tra phạm vi Tenant, địa điểm, máy; chỉ Platform Super Administrator xem tổng hợp toàn nền tảng.
  - **Task P5-11 — Dashboard, bộ lọc và xuất báo cáo**
    - Dashboard theo vai trò, lọc thương hiệu/địa điểm/máy/sản phẩm và ngày-tuần-tháng/khoảng tùy chọn.
    - Trực quan hóa trạng thái máy, doanh thu, tỷ lệ xịt thành công/thất bại, alert, refill và maintenance.
    - Xuất báo cáo CSV hoặc Excel theo dữ liệu người dùng được phép xem.
    - Hiển thị audit log và lịch sử khi API người 1 cung cấp, tuân thủ quyền đọc.
  - **Task P5-12 — Kiểm thử giao diện, E2E UI và hướng dẫn sử dụng**
    - Test từ chọn mùi → tạo order → QR → thanh toán → xịt → kết quả; test thất bại/offline/expired.
    - Test menu và dữ liệu theo vai trò/Tenant; màn hình low-stock, alert, maintenance, refund/review.
    - Kiểm tra responsive, cảm ứng, trạng thái loading/lỗi và tự quay về màn hình chính kiosk.
    - Bàn giao hai giao diện + Reporting, hướng dẫn sử dụng từng vai trò, kết quả UI/E2E test.

**Bàn giao của người 5:** Kiosk UI; Admin Portal theo role; Reporting Service & Dashboard; CSV/Excel export; UI tests; user guide. **Không có trách nhiệm tự sửa lỗi backend của người 1–4.**

---

## VII. MA TRẬN BAO PHỦ 17 NHÓM YÊU CẦU CỦA DOCX

| Nhóm chức năng (DOCX) | Người phụ trách chính | Task bao phủ |
|---|---|---|
| 1. Platform Super Administrator | 1 | P1-01, P1-02, P1-04, P1-06, P1-09 |
| 2. Brand Administrator | 1, 2, 4 (từng miền) | P1-04, P2-01, P2-02, P4-01, P4-02 |
| 3. Operations Manager | 2, 3, 4 | P2-10, P3-08, P3-09, P4-03, P4-06 |
| 4. Technician | 2 (ticket/checklist), 4 (diagnostic) | P2-10, P2-11, P4-09 |
| 5. Inventory Staff | 2 | P2-04 đến P2-09 |
| 6. Customer/Kiosk User | 5 (UI), 3–4 (xử lý) | P5-01 đến P5-05, P3-01 đến P3-07, P4-07 |
| 7. Brand, User, Access | 1 | P1-01 đến P1-05 |
| 8. Machine & Slot | 4 (máy/slot), 2 (bottle) | P4-01 đến P4-03, P2-06 |
| 9. Order & Payment | 3 | P3-01 đến P3-05, P3-08 đến P3-10 |
| 10. Secure Dispensing | 4 (thiết bị), 3 (order) | P4-07 đến P4-09, P3-06, P3-07 |
| 11. IoT Communication | 4 | P4-04 đến P4-05, P4-10 |
| 12. Perfume Inventory | 2 | P2-04 đến P2-09 |
| 13. Alerts & Notification | 4 (alert), 1 (gửi thông báo) | P4-06, P1-10 |
| 14. Maintenance | 2 (workflow), 4 (máy) | P2-10, P2-11, P4-09 |
| 15. Dashboard & Reporting | 5 (tổng hợp), 1–4 (API nguồn) | P5-10, P5-11, P1-11, P2-12, P3-10, P4-05 |
| 16. Audit & Traceability | 1 (chuẩn/lưu), cả nhóm (phát sự kiện) | P1-09; P2-02, P2-09; P3-10; P4-02; P5-09 |
| 17. Business Rules | Mọi người, theo module | P1-05, P2-06/07, P3-01/05/06/07, P4-07/08/09, P5-12 |

> Ma trận dùng từ **“phụ trách chính”** theo từng miền nghiệp vụ; phần UI tương ứng do người 5 thực hiện. Không có nghĩa người đứng đầu phải tự làm toàn bộ các tác vụ liên service.

## VIII. CONTRACT CẦN CHỐT TRƯỚC KHI CODE SONG SONG

- **Identity → tất cả service:** `tenantId`, `userId`, role/permission/scope; cách xác minh quyền và phân biệt admin platform với admin tenant.
- **Catalog/Inventory → Order/Machine:** sản phẩm, giá hiện hành, dữ liệu chai/lõi đang lắp, mức tồn có thể xịt, thông báo low-stock.
- **Machine → Order:** kiểm tra máy/slot có sẵn sàng nhận đơn, command ID, ACK, execution result, error code và timeout.
- **Order → Machine:** chỉ phát yêu cầu xịt cho order đã được backend xác minh thanh toán; unique idempotency key và hạn hiệu lực.
- **Machine → Inventory:** kết quả xịt xác nhận (customer/diagnostic) kèm unique command ID để trừ tồn kho đúng một lần.
- **Machine Alert → Operation/Notification:** sự cố nghiêm trọng để tạo ticket; sự cố/thay trạng thái để gửi thông báo.
- **Các service → Audit & Reporting:** sự kiện có `eventId`, Tenant, đối tượng, thời gian, actor/source và các trường chỉ cần thiết cho ghi vết/báo cáo.
- **Backend → Frontend:** thống nhất HTTP status, error code, pagination/filter, trường thời gian và trạng thái để giao diện không phải đoán nghiệp vụ.

*Tên sự kiện và trường trên là **đề xuất contract thực hiện** để đáp ứng đặc tả, không phải tên API đã được DOCX quy định.*

## IX. KIỂM THỬ TÍCH HỢP CHUNG — CẢ 5 NGƯỜI CÙNG CHỊU TRÁCH NHIỆM

- **E2E-01 — Happy path:** máy online, slot có hàng → chọn mùi → giá được chốt → QR → xác nhận thanh toán backend → một lần xịt → cập nhật order & inventory → dashboard.
- **E2E-02 — Không có quyền:** Brand A không truy cập được thương hiệu B; Viewer không được refund/sửa kho/diagnostic; thiết bị thu hồi không gửi lệnh hợp lệ.
- **E2E-03 — Trùng sự kiện:** webhook trùng, request tạo lệnh trùng, MQTT command trùng và báo kết quả trùng không gây xịt hoặc trừ tồn kho hai lần.
- **E2E-04 — Máy không an toàn:** offline, cửa mở, bảo trì, slot rỗng, sensor/actuator lỗi → không tạo order/xịt tùy điểm kiểm tra.
- **E2E-05 — Thanh toán ngoại lệ:** sai chữ ký, sai số tiền, expired, cancelled, callback đến muộn → không phát lệnh xịt trái phép.
- **E2E-06 — Kết quả không xác định:** đã thanh toán nhưng MQTT timeout → review, không tự động xịt lần hai.
- **E2E-07 — Bảo trì:** critical alert → ticket → maintenance mode → checklist/sửa chữa → test thành công → active.
- **E2E-08 — Tồn kho:** nạp chai đúng slot, xịt xác nhận, low-stock, điều chỉnh có lý do, xem audit/history và báo cáo.
- **E2E-09 — Thiết bị mất mạng:** thiết bị lưu sự kiện và đồng bộ lại sau reconnect, không nhân đôi event/lệnh xịt.
- **E2E-10 — Bàn giao:** chạy hệ thống theo README/Compose, demo 4 slot, có kết quả test, tài liệu kỹ thuật và user guide.

### Quy tắc trách nhiệm khi test lỗi

- **Lỗi trong service riêng:** người sở hữu service xử lý.
- **Lỗi giữa hai service:** hai chủ service phối hợp theo API contract; không chuyển mặc định cho người 5.
- **Lỗi giao diện:** người 5 xử lý; backend không đúng contract thì chủ backend xử lý.
- **Lỗi tích hợp toàn hệ thống:** cả 5 cùng test và xác định module nguồn lỗi; một người được nhóm thống nhất ghép Docker Compose từ cấu hình của từng người.

## X. GIT, PULL REQUEST & TIÊU CHÍ HOÀN THÀNH TASK

- Không commit trực tiếp lên `main`; mỗi người làm việc trên branch đã phân công, có thể tách nhánh/PR nhỏ cho từng task.
- Trước mỗi PR liên quan service khác: cập nhật API contract, DTO/schema hoặc MQTT payload mẫu.
- PR phải có mô tả task, thay đổi API/database, test đã chạy, dữ liệu mẫu và ảnh/video demo nếu là UI/thiết bị.
- Một task chỉ được đánh dấu **Done** khi: code chạy được, test tình huống chính/lỗi, tích hợp được với contract đã thống nhất, cập nhật README và được review.
- Ưu tiên hoàn thành **luồng E2E cơ bản trước**, sau đó tăng độ hoàn chỉnh cho Alert, Maintenance, báo cáo, bảo mật và các tình huống ngoại lệ; **ưu tiên triển khai không đồng nghĩa với được phép bỏ yêu cầu đã ghi trong DOCX**.

## XI. GHI CHÚ VỀ CÂN BẰNG CÔNG VIỆC

| Thành viên | Số task đầu mối | Đặc điểm công sức cần tính khi lập Sprint |
|---|---:|---|
| Người 1 | 11 | Identity/Multi-Tenant và chuẩn kỹ thuật được dùng chung trên mọi service |
| Người 2 | 12 | Hai nghiệp vụ có trạng thái phức tạp: Inventory + Maintenance |
| Người 3 | 11 | Ít giao diện, nhưng yêu cầu tính đúng đắn giao dịch/idempotency cao |
| Người 4 | 12 | Có phần mềm IoT **và phần cứng prototype**, rủi ro công sức lớn nhất cần được theo dõi |
| Người 5 | 12 | Nhiều màn hình cho nhiều vai trò cùng Dashboard, nhưng không sở hữu lỗi backend |


---

