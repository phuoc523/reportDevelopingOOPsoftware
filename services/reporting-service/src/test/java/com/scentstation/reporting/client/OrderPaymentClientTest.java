
package com.scentstation.reporting.client;

import com.scentstation.reporting.dto.OrderPaymentResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderPaymentClientTest {

    private RestClient.Builder restClientBuilder;
    private RestClient restClient;
    private RestClient.RequestHeadersUriSpec<?> requestSpec;
    private RestClient.RequestHeadersSpec<?> headersSpec;
    private RestClient.ResponseSpec responseSpec;

    private OrderPaymentClient orderPaymentClient;

    private static final String BASE_URL = "http://localhost:8083";
    private static final String REFERENCE = "PAY-TEST-001";

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {

        restClientBuilder = mock(RestClient.Builder.class);
        restClient = mock(RestClient.class);
        requestSpec = mock(RestClient.RequestHeadersUriSpec.class);
        headersSpec = mock(RestClient.RequestHeadersSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClientBuilder.baseUrl(BASE_URL))
                .thenReturn(restClientBuilder);

        when(restClientBuilder.requestFactory(any(
                org.springframework.http.client.ClientHttpRequestFactory.class
        ))).thenReturn(restClientBuilder);

        when(restClientBuilder.build())
                .thenReturn(restClient);

        orderPaymentClient = new OrderPaymentClient(
                restClientBuilder,
                BASE_URL
        );

        when(restClient.get())
                .thenReturn((RestClient.RequestHeadersUriSpec) requestSpec);

        doReturn(headersSpec)
        .when(requestSpec)
        .uri(
                eq("/api/v1/payments/{reference}"),
                eq(REFERENCE)
        );

        when(headersSpec.retrieve())
                .thenReturn(responseSpec);
    }

    @Test
    void getPaymentWhenOrderServiceReturns404ShouldReturn404() {

        when(responseSpec.body(OrderPaymentResponse.class))
                .thenThrow(
                        new HttpClientErrorException(
                                HttpStatus.NOT_FOUND
                        )
                );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderPaymentClient.getPayment(REFERENCE)
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }

    @Test
    void getPaymentWhenOrderServiceReturns500ShouldReturn502() {

        when(responseSpec.body(OrderPaymentResponse.class))
                .thenThrow(
                        new HttpServerErrorException(
                                HttpStatus.INTERNAL_SERVER_ERROR
                        )
                );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderPaymentClient.getPayment(REFERENCE)
        );

        assertEquals(
                HttpStatus.BAD_GATEWAY,
                exception.getStatusCode()
        );
    }

    @Test
    void getPaymentWhenConnectionFailsShouldReturn502() {

        when(responseSpec.body(OrderPaymentResponse.class))
                .thenThrow(
                        new ResourceAccessException(
                                "Connection refused",
                                new IOException("Connection refused")
                        )
                );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderPaymentClient.getPayment(REFERENCE)
        );

        assertEquals(
                HttpStatus.BAD_GATEWAY,
                exception.getStatusCode()
        );
    }

    @Test
    void emptyResponseReturns502() {
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> orderPaymentClient.getPayment(REFERENCE)).getStatusCode());
    }

    @Test
    void timeoutReturns502() {
        when(responseSpec.body(OrderPaymentResponse.class)).thenThrow(
                new ResourceAccessException("Read timed out", new java.net.SocketTimeoutException()));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> orderPaymentClient.getPayment(REFERENCE)).getStatusCode());
    }

    @Test
    void malformedResponseReturns502() {
        when(responseSpec.body(OrderPaymentResponse.class))
                .thenThrow(new org.springframework.web.client.RestClientException("Invalid JSON"));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> orderPaymentClient.getPayment(REFERENCE)).getStatusCode());
    }

    @Test
    void validResponseIsReturnedUnchanged() {
        var payment = new OrderPaymentResponse(REFERENCE, 1L, java.math.BigDecimal.ONE, "VND",
                "PENDING", null, java.time.LocalDateTime.now(), null, null);
        when(responseSpec.body(OrderPaymentResponse.class)).thenReturn(payment);
        assertSame(payment, orderPaymentClient.getPayment(REFERENCE));
    }
}
