# Kiến trúc LITE

Base `61bb984` chứa launcher `80e4fee`. Main `bef3105` là tổ tiên của launcher. Nhánh IoT `b452e21` tách tại `f3ca9c1`, thêm MQTT/thiết bị và không cần cho simulator Java tích hợp.

Giữ gateway, Identity/RBAC, Catalog, Order, Machine, Redis (rate limiting) và bốn MySQL schema/volume độc lập. Nginx phục vụ ba frontend đã build và chỉ bind loopback. LITE chỉ khởi động dependency của lite-web. Không khởi động Kafka, Reporting, Operation, Notification, các DB phụ, monitoring, MQTT, NiFi hay firmware. Admin dùng Orders/Payments và Machines qua API gốc. Các màn hình phụ thuộc reporting/operation/notification thuộc FULL.

Order dùng provider MOCK với SePay tắt và các thiết lập ngân hàng trống. Mock Web gửi yêu cầu cùng origin; Nginx thêm token callback phía server, giới hạn tốc độ và chỉ chuyển request đến Order nội bộ. Machine dùng simulator có sẵn, không kết nối broker hay phần cứng. LITE có project, env và volumes riêng. Compose FULL giữ cấu hình gốc.
