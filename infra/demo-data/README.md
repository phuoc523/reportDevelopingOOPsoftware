# Dữ liệu demo ScentStation

Bộ seed này tạo dữ liệu liên kết giữa Identity, Catalog, Machine, Operation,
Order và Reporting. Các câu lệnh dùng khóa ổn định và `ON DUPLICATE KEY UPDATE`,
vì vậy có thể chạy lại trên cùng môi trường local.

## Cách chạy

```powershell
docker compose up -d
powershell -ExecutionPolicy Bypass -File .\infra\demo-data\seed-demo.ps1
```

Chờ các service khởi động xong, sau đó mở Admin Web tại
`http://localhost:7514`.

## Tài khoản đăng nhập local

Tất cả tài khoản dưới đây dùng mật khẩu local chung: `Demo@2026!`

| Tài khoản | Phạm vi | Vai trò | Mục đích kiểm thử |
| --- | --- | --- | --- |
| `platform.admin` | Toàn nền tảng | Platform Super Admin | Xem và quản trị tổng thể các thương hiệu |
| `brand.admin` | ScentStation | Brand Admin | Quản trị thương hiệu ScentStation |
| `chanel.admin` | Chanel | Brand Admin | Quản trị thương hiệu Chanel |
| `dior.admin` | Dior | Brand Admin | Quản trị thương hiệu Dior |
| `scent.ops` | ScentStation | Operations Manager | Vận hành kiosk, cảnh báo và bảo trì |
| `scent.tech` | ScentStation | Technician | Kỹ thuật viên có phạm vi kiosk được giao |
| `scent.inventory` | ScentStation | Inventory Staff | Nghiệp vụ tồn kho |
| `kiosk.customer` | ScentStation / máy `9101` | Customer | Trải nghiệm luồng Kiosk, tạo đơn và theo dõi thanh toán |

Đây là dữ liệu demo được commit để các thành viên có cùng môi trường phát triển.
Chạy lại seed sẽ đồng bộ hồ sơ và mật khẩu của các tài khoản trên; không dùng
bộ tài khoản này cho staging hoặc production.

## Dữ liệu nghiệp vụ

- 3 tenant: ScentStation, Chanel và Dior.
- 8 sản phẩm và 5 chiến dịch; riêng ScentStation đã hoàn thiện 4 sản phẩm để kiểm thử Kiosk.
- 3 kiosk tại 3 địa điểm, mỗi kiosk thuộc một thương hiệu; ScentStation đã có đủ 4 đầu phun, Chanel và Dior đang là dữ liệu từng phần cho nhóm tiếp theo.
- 5 chai tồn kho: 3 còn nhiều, 1 gần hết, 1 đã hết.
- Cảnh báo tồn kho/kết nối, phiếu bảo trì, đơn hàng, thanh toán và giao dịch mẫu.

Không dùng bộ mật khẩu hoặc dữ liệu này cho môi trường production.
