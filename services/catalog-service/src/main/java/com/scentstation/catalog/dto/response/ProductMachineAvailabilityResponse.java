package com.scentstation.catalog.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductMachineAvailabilityResponse {

    private Long tenantId;
    private Long productId;
    private Long machineId;

    // Trạng thái bật/tắt sản phẩm được quản lý bởi Catalog.
    private Boolean catalogAvailable;

    // Trạng thái product thực sự có thể phục vụ trên machine.
    private Boolean machineAvailable;

    // Số slot khả dụng cho product trên machine.
    private Integer availableSlotCount;

    // Product chỉ khả dụng cuối cùng khi cả Catalog và Machine đều cho phép.
    private Boolean available;
}