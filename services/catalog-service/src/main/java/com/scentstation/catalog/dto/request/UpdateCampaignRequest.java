package com.scentstation.catalog.dto.request;

import com.scentstation.catalog.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class UpdateCampaignRequest {

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

    @NotNull
    private Boolean active;
}