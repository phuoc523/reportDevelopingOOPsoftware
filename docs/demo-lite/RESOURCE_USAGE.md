# RAM và tài nguyên

Windows đo được 12.356.920 KiB RAM khả kiến và 2.684.208 KiB khả dụng (2,56 GiB) tại kiểm tra ban đầu. Docker Desktop Linux Engine chưa chạy.

Ngân sách thử ban đầu gồm 5 JVM x 384 MiB (heap 48–192 MiB), 4 MySQL x 384 MiB (buffer pool 64 MiB), Redis 128 MiB và Nginx 64 MiB. Tổng trần cấu hình 3.648 MiB. Đây là cấu hình thử nghiệm. Dùng Check trước và sau một lượt Verify để ghi RAM từng container và Windows. Runtime và số liệu sau giao dịch chưa đo do thiếu RAM khả dụng.

CPU Intel Core i3-1115G4, 4 logical processors. Drive E: 518,587,940,864 bytes free of 1,000,196,800,512 bytes. Default WSL version 2. Later resource check measured 1,511,772 KiB available.
