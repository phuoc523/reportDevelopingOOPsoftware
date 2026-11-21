1. Mục tiêu
   - Hiện tại chỉ cần sao cho QR quét tới chi tiết đơn của người đó thôi nghĩa là 1 QR khi quét sẽ dẫn tới 1 đơn hàng (có nút thanh toán) và đơn hàng đó chỉ thuộc về QR đó thôi. Không được có trường hợp: 1 QR trỏ tới nhiều đơn hàng
   - Còn vấn đề trỏ tới backend bị lỗi thì cái đó để sau trước tiên làm í 1 trước. Sau khi xong í 1 tui sẽ xem lại và fix sau
2. Vấn đề
   - Trong QR hiện tại á khi quét là nó sẽ cho kiểu Key (primary key trong database để xác nhận đơn hàng)
   - Tình hình: với source này thì khi mà khách ấn thanh toán mua chai/xịt thử thì có sẽ hiện 1 QR, QR đó chưa được quét ngay, khi nhìn trên QR đó sẽ thấy 1 link đó là link truy cập mock payment (đã tích hợp mock payment) để thanh toán (link đó để mặc định là vậy khi test trên máy thì có thể không phải link đó thì check link chính xác bằng cách là <IPV4 của Wireless Adapter Wi-Fi check trong ipconfig>:3000 port 3000 cố định mở sẵn khi chạy docker VD như: IPV4 của Wireless Adapter Wi-Fi check trong ipconfig của máy hiện tại là: 192.168.11.27 thì link sẽ là: 192.168.11.37:3000). Sau khi khách truy cập link xong sẽ dẫn tới 1 trang đó là trang mock payment khi bấm quét thì quét không được do có thể 2 trường hợp xảy ra: Một - Web chặn không cho phép quét. Hai - quét được nhưng không nhận dạng được QR
   - Nói chung mong muốn là QR -> Trang chi tiết đơn (Mock Payment), hiển thị thông tin đơn hàng, có nút thanh toán
3. Hướng giải quyết (dự định không nhất thiết phải theo có thể cách khác miễn hoàn thành mục tiêu)
   - Có thể chấp nhận các trên nhưng: Một - phải làm sao để để link có quyền truy cập hoặc Hai - phải thêm 1 source nào đó bổ trợ cho việc có thể quét để dẫn tới chi tiết đơn trang
   - Điều chỉnh nội dung URL được mã hóa trong QR để khi quét, khách hàng được dẫn trực tiếp đến trang chi tiết của đơn hàng tương ứng thay vì dẫn thẳng đến Mock Payment
   - Tạo một nơi lưu trữ thông tin đơn hàng và mã định danh tương ứng. Mỗi QR chứa một mã định danh duy nhất để hệ thống có thể xác định và hiển thị đúng đơn hàng khi khách truy cập
   - Có thể đề xuất kiến trúc hoặc cách triển khai khác phù hợp hơn, miễn đảm bảo tính duy nhất giữa QR và đơn hàng
