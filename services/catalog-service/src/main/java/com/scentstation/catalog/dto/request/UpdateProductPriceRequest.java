package com.scentstation.catalog.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter 
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductPriceRequest {
    @NotNull 
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal defaultPrice;
    
}
