package com.scentstation.order.service;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SepayCredit(
        @NotNull @Positive Long id,
        @NotBlank @Size(max = 40) String gateway,
        @NotBlank @Size(max = 20) String accountNumber,
        @Size(max = 64) String code,
        @Size(max = 1024) String content,
        @NotBlank @Size(max = 8) String transferType,
        @NotNull @Digits(integer = 15, fraction = 0) BigDecimal transferAmount,
        @Size(max = 64) String referenceCode,
        @NotBlank @Size(max = 19) String transactionDate
) {}
