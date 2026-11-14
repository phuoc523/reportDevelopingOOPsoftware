# PHÂN HỆ CATALOG & PROMOTIONAL PRICING SERVICE (:8082)

## 1. Giới thiệu phân hệ
`catalog-service` đảm nhận quản lý danh mục nước hoa và chính sách giá:
- Sở hữu cơ sở dữ liệu `catalog_db`.
- Quản lý thực thể sản phẩm nước hoa (`Product`) và chiến dịch khuyến mãi (`Campaign`) theo từng Tenant đối tác.
- Cung cấp công cụ tính giá bán hiệu lực (`PromotionalPricingService`) dựa trên loại chiết khấu (`PERCENTAGE`, `FIXED_AMOUNT`) và thời gian chiến dịch.
- Đóng vai trò là nguồn dữ liệu thẩm quyền duy nhất (*Authoritative Source*) về giá để Order Service đối chiếu snapshot khi tạo đơn.

## 2. Danh mục sơ đồ chi tiết
1. **`use-case.puml`**:
   - Quản lý sản phẩm, tìm kiếm tiêu chí, cấu hình chiến dịch khuyến mãi, tra cứu giá hiệu lực và xuất snapshot đơn hàng.
   - Quan hệ kế thừa `<<generalization>>` giữa hai loại hình khuyến mãi (% và số tiền cố định).
2. **`class-diagram.puml`**:
   - Thực thể: `Product`, `Campaign`, `DiscountType`.
   - DTO & Mappers: `ProductResponse`, `EffectivePriceResponse`, `OrderProductSnapshotResponse`.
   - Tầng điều khiển & dịch vụ: `ProductController`, `PromotionalPricingController`, `InternalOrderProductController`, `ProductService`, `CampaignService`, `PromotionalPricingService`.
   - Client ngoại vi: `MachineAvailabilityClient` (kiểm tra độ phủ sản phẩm trên các trạm máy).
3. **`sequence-diagram.puml`**:
   - Luồng nghiệp vụ Order Service gọi lấy snapshot thẩm quyền: Truy vấn CSDL $\rightarrow$ Lọc chiến dịch hiệu lực $\rightarrow$ Tính mức giảm $\rightarrow$ Trả snapshot có bảo vệ Service Key.
4. **`activity-diagram.puml`**:
   - Biểu đồ phân làn tính toán giá: Kiểm tra tồn tại $\rightarrow$ Kiểm tra trạng thái bán $\rightarrow$ Áp dụng công thức chiết khấu $\rightarrow$ Xuất snapshot chốt giá.
