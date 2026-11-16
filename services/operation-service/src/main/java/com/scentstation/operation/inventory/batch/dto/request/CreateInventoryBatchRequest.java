package com.scentstation.operation.inventory.batch.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateInventoryBatchRequest {

    @NotNull
    @Positive
    private Long tenantId;

    @NotNull
    @Positive
    private Long productId;

    @NotBlank
    private String batchCode;

    @NotNull
    @Positive
    private Integer receivedQuantity;

    @NotNull
    @PastOrPresent
    private LocalDate receivedAt;

    private LocalDate expiryDate;
}