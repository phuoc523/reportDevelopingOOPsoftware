package com.scentstation.operation.inventory.batch.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryBatchResponse {

    private Long id;
    private Long tenantId;
    private Long productId;
    private String batchCode;
    private Integer receivedQuantity;
    private LocalDate receivedAt;
    private LocalDate expiryDate;
    private Boolean expired;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}