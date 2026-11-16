package com.scentstation.reporting.client;

import com.scentstation.reporting.dto.OrderPaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class OrderPaymentClient {

    private final RestClient restClient;

    public OrderPaymentClient(
            RestClient.Builder restClientBuilder,
            @Value("${integration.order.base-url}") String baseUrl) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory())
                .build();
    }

    public OrderPaymentResponse getPayment(String reference) {
        try {
            OrderPaymentResponse payment = restClient.get()
                    .uri("/api/v1/payments/{reference}", reference)
                    .retrieve()
                    .body(OrderPaymentResponse.class);

            if (payment == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Order Service returned an empty response");
            }
            return payment;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found in Order Service");
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Order Service returned an error",
                    ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cannot communicate with Order Service",
                    ex);
        }
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }
}
