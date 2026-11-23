# ScentStation FULL DEMO LITE

Windows PowerShell, Docker Desktop Linux và Compose 2.40. Chuẩn bị 6 GiB RAM khả dụng cho build tuần tự, 4,5 GiB cho startup, Linux Engine ít nhất 4 GiB. Chạy build lần đầu với `Start-ScentStation-Lite.ps1 -Build`.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo\Start-ScentStation-Lite.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\demo\Stop-ScentStation-Lite.ps1
```

- Kiosk http://127.0.0.1:17515
- Admin http://127.0.0.1:17514
- Mock http://127.0.0.1:17516/mock-payment/<reference> trên chính máy chạy demo.

Dùng tài khoản demo tương ứng trong Identity. Nếu DB mới chưa có dữ liệu phù hợp, tạo khách hàng, tài khoản admin tenant, sản phẩm, bảng giá và slot máy qua quy trình seed/API của các dịch vụ gốc. Verify nhận hai PSCredential và file JSON chứa payload TRIAL đúng hợp đồng Kiosk. Lưu ID đầu ra làm bằng chứng. Stop chỉ dừng project LITE và giữ dữ liệu.

For the first startup, the operator can supply LITE_BOOTSTRAP_USERNAME, LITE_BOOTSTRAP_EMAIL and LITE_BOOTSTRAP_PASSWORD through the local PowerShell environment. Remove them after provisioning, then restart/recreate Identity to clear container environment. Create a tenant-scoped admin for the demonstration. The existing infra/demo-data fixtures document the demo machine, catalog, tenant and kiosk accounts. The existing FULL seed launcher targets the FULL project; provision LITE through its own APIs or import individually reviewed fixtures into fresh LITE databases. Set LITE_KIOSK_MACHINE_ID to the fixture machine ID before building Kiosk. The default is 9101, matching the source UI default.

Set LITE_MACHINE_OUTCOME to SUCCESS, FAILURE or TIMEOUT before recreating the LITE Machine service. LITE acknowledges the command, then resolves success or failure on a status check after 2.5 seconds. TIMEOUT leaves the command acknowledged until the expiry job runs.
