# API Gateway

Gateway là điểm vào Spring Cloud Gateway WebFlux trên cổng 8080. Nó định tuyến API của frontend, áp dụng CORS, chuẩn hóa header ở ranh giới, tạo request ID và giới hạn lưu lượng qua Redis.

Gateway dùng Identity introspection để lấy khóa giới hạn dựa trên principal. Các endpoint nội bộ đi qua mạng liên dịch vụ. Xem [quy ước API](../../docs/api-conventions.md), [bộ giới hạn Gateway](../../docs/redis/gateway.md) và [kiến trúc Gateway](../../architecture/api-gateway/README.md).

Xem [hướng dẫn Identity và Platform](../../docs/identity-platform.md) để chạy demo và kiểm thử Gateway.
