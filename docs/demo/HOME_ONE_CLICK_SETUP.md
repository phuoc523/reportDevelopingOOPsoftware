# Chạy ScentStation trên máy cá nhân

Cần Windows hỗ trợ WSL 2, Docker Desktop đang chạy Linux Engine, Compose 2.24.4 trở lên, Git, ít nhất 8 GiB RAM khả dụng và 20 GiB trống trên ổ chứa repo. Docker tải JDK và Node trong lúc build image. Cài Docker theo [hướng dẫn chính thức](https://docs.docker.com/desktop/setup/install/windows-install/) và kiểm tra `wsl --version`, `docker version` và `docker compose version` trước khi chạy. Docker Desktop phải có context cục bộ `desktop-linux`.

```powershell
git clone --branch feature/phung-full-demo-oneclick https://github.com/phuoc523/reportDevelopingOOPsoftware.git
cd reportDevelopingOOPsoftware
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo\Start-ScentStation-Full.ps1
```

Lệnh tạo `.env.demo` ở lần đầu để lưu khóa ngẫu nhiên và ID project. Giữ file này cùng bản clone giữa các lần chạy để dùng lại đúng volume. Có thể chuẩn bị trước bằng `.\scripts\demo\Prepare-LocalEnv.ps1`. File `.env` thông thường có thể dùng riêng cho cấu hình ngoài demo. Tùy chọn `-ExecutionPolicy Bypass` chỉ áp dụng cho tiến trình PowerShell của lệnh này.

Chạy `.\scripts\demo\Check-ScentStation-Full.ps1` để xem trạng thái. Dùng `.\scripts\demo\Stop-ScentStation-Full.ps1` để dừng và giữ dữ liệu. Chạy Start lần nữa để tiếp tục, hoặc thêm `-Build` sau khi sửa mã nguồn.

Nếu Engine chưa chạy, mở Docker Desktop và đợi Linux Engine sẵn sàng. Nếu launcher báo cổng bị chiếm, kiểm tra quyền sở hữu ở Windows. Chỉ dừng server do bạn quản lý rồi chạy lại.
