# Kiểm thử LITE

Trạng thái PARTIAL. Chưa chạy runtime LITE và E2E trên Windows. Lần kiểm tra tài nguyên ban đầu còn 2,56 GiB khả dụng, Docker Desktop Linux Engine chưa chạy. Không có Maven CLI trên PATH.

Khi đủ tài nguyên, chạy Start -Build, Check, Verify với fixture tenant/machine/slot và thực hiện lại luồng bằng trình duyệt. Ghi rõ trạng thái PENDING, PAID, DISPENSED (hoàn tất), command ID và lịch sử Admin. Kiểm tra riêng thất bại hoặc timeout, không trả tiền thật và không kết nối máy vật lý. Ghi RAM của lượt phục vụ và kết quả build.

Compose 2.40 config PASS for lite-web and its 11-service dependency tree. Windows PowerShell parser PASS for all five LITE scripts. Start refused to launch at 0.71 GiB available, before running Docker. Business E2E remains NOT TESTED.

Node 24 production build PASS for Admin, Kiosk and Mock Payment. The built mock asset contains the relative API route and no callback token/header or localhost Order URL.

Machine Service compiled main and test sources with Java release 21. Maven 3.9.16 on JDK 25 required net.bytebuddy.experimental=true for the test JVM. Final machine suite ran 64 tests with 62 passes and two failures. LiteDispenseSimulationTest and both DispenseCommandIntegrationTest cases passed. Remaining failures were IdentityIntrospectionClientTest.timeoutFailsClosed (CancellationException) and LocationListCacheIntegrationTest.cachedLocationsAreTenantScopedAndEvictable (response equality). Neither failure was marked as a pass.
A separate Maven run of the two failing methods on source commit 61bb984 reproduced both failures under the same JDK 25, so neither is a new LITE regression.
