package com.scentstation.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ScentStation Order Service API")
                .version("v1")
                .description("""
                        Order and mock-payment API. No database is required; service data is in memory.

                        **Swagger demo:** execute `POST /api/orders` using its valid example body. Copy the returned \
                        `id` into `POST /api/orders/{orderId}/payments/qr`. For payment, call the returned \
                        reference's `/simulate` endpoint, then inspect `/api/payments/{reference}` and \
                        `/api/orders/{id}/status`. To demonstrate a provider callback instead of `/simulate`, \
                        create a new order and QR payment, then call `POST /api/payments/callback` with the values \
                        returned by QR and the `X-Payment-Callback-Token` header. The local default token is \
                        `local-mock-callback-token`; deployments may configure `PAYMENT_CALLBACK_TOKEN`.

                        Validation and business errors use `ApiErrorResponse`: `status`, `error`, `message`, \
                        `path`, and `timestamp`. HTTP 400 means malformed JSON, invalid fields, or mismatched \
                        callback details. HTTP 401 means the callback token is missing or invalid, HTTP 404 means \
                        an order/payment was not found, and HTTP 409 means a state transition or payment conflict.
                        """));
    }
}
