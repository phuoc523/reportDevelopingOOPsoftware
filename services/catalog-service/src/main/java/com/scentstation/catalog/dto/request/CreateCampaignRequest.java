package com.scentstation.catalog.dto.request;

import com.scentstation.catalog.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class CreateCampaignRequest {

    @NotNull
    @Positive
    private Long tenantId;

    @NotNull
    @Positive
    private Long productId;

    @NotBlank
    private String name;

    @NotNull
    private DiscountType discountType;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal discountValue;

    @NotNull
    private LocalDateTime startAt;

    @NotNull
    private LocalDateTime endAt;

    private Boolean active = true;
}