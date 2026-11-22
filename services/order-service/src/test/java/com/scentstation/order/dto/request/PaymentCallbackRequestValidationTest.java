package com.scentstation.order.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentCallbackRequestValidationTest {
    @Test
    void acceptsValuesAtTheStorageLimits() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var request = new PaymentCallbackRequest(
                    "event-1", "R".repeat(64), 1L, new BigDecimal("999999999999999.9999"), "VND", "PAID");

            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    @Test
    void rejectsValuesOutsideTheCallbackContract() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var request = new PaymentCallbackRequest(
                    "event-1", "R".repeat(65), 0L, new BigDecimal("1000000000000000.00001"), "VND", "PAID");

            assertThat(factory.getValidator().validate(request))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("reference", "orderId", "amount");
        }
    }
}
