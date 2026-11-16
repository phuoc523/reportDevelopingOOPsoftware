package com.scentstation.reporting.service;

import com.scentstation.reporting.client.OrderPaymentClient;
import com.scentstation.reporting.dto.OrderPaymentResponse;
import com.scentstation.reporting.dto.TransactionResponse;
import com.scentstation.reporting.dto.TransactionSyncRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderPaymentSyncServiceTest {

    @Mock
    private OrderPaymentClient orderPaymentClient;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private OrderPaymentSyncService orderPaymentSyncService;

    @Test
    void syncFromOrderMapsPaymentAndDelegatesToTransactionService() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 13, 10, 0, 0);
        LocalDateTime paidAt = LocalDateTime.of(2026, 11, 13, 10, 1, 0);
        OrderPaymentResponse payment = new OrderPaymentResponse(
                "PAY-1", 8L, new BigDecimal("25.50"), "VND", "PAID",
                "qr", createdAt, createdAt.plusMinutes(15), paidAt);
        TransactionResponse expected = new TransactionResponse(
                1L, "PAY-1", "8", new BigDecimal("25.50"), "VND", "PAID", createdAt, paidAt);
        when(orderPaymentClient.getPayment("PAY-1")).thenReturn(payment);
        when(transactionService.syncTransaction(org.mockito.ArgumentMatchers.any()))
                .thenReturn(expected);

        TransactionResponse actual = orderPaymentSyncService.syncFromOrder("PAY-1");

        assertEquals(expected, actual);
        ArgumentCaptor<TransactionSyncRequest> captor = ArgumentCaptor.forClass(TransactionSyncRequest.class);
        verify(transactionService).syncTransaction(captor.capture());
        assertEquals("8", captor.getValue().orderId());
        assertEquals("PAID", captor.getValue().status());
    }

    @Test
    void syncFromOrderRejectsIncompletePaymentPayload() {
        when(orderPaymentClient.getPayment("PAY-1")).thenReturn(new OrderPaymentResponse(
                "PAY-1", null, new BigDecimal("25.50"), "VND", "PAID",
                null, LocalDateTime.now(), null, LocalDateTime.now()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderPaymentSyncService.syncFromOrder("PAY-1"));
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        verifyNoInteractions(transactionService);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("invalidPayments")
    void invalidUpstreamDataReturns502WithoutWriting(OrderPaymentResponse payment) {
        when(orderPaymentClient.getPayment("PAY-1")).thenReturn(payment);
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> orderPaymentSyncService.syncFromOrder("PAY-1")).getStatusCode());
        verifyNoInteractions(transactionService);
    }

    static java.util.stream.Stream<OrderPaymentResponse> invalidPayments() {
        var now = LocalDateTime.of(2026, 11, 13, 10, 0);
        return java.util.stream.Stream.of(
                null,
                new OrderPaymentResponse("OTHER", 1L, BigDecimal.ONE, "VND", "PENDING", null, now, null, null),
                new OrderPaymentResponse("PAY-1", 0L, BigDecimal.ONE, "VND", "PENDING", null, now, null, null),
                new OrderPaymentResponse("PAY-1", 1L, BigDecimal.valueOf(-1), "VND", "PENDING", null, now, null, null),
                new OrderPaymentResponse("PAY-1", 1L, BigDecimal.ONE, " ", "PENDING", null, now, null, null),
                new OrderPaymentResponse("PAY-1", 1L, BigDecimal.ONE, "VND", "UNKNOWN", null, now, null, null),
                new OrderPaymentResponse("PAY-1", 1L, BigDecimal.ONE, "VND", "PAID", null, now, null, null),
                new OrderPaymentResponse("PAY-1", 1L, BigDecimal.ONE, "VND", "PAID", null, now, null, now.minusSeconds(1)),
                new OrderPaymentResponse("PAY-1", 1L, BigDecimal.ONE, "VND", "PENDING", null, now, null, now));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"   "})
    void blankReferenceDoesNotCallOrder(String reference) {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> orderPaymentSyncService.syncFromOrder(reference)).getStatusCode());
        verifyNoInteractions(orderPaymentClient, transactionService);
    }

    @Test
    void unavailableOrderDoesNotChangeStoredTransactions() {
        when(orderPaymentClient.getPayment("PAY-1"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cannot communicate with Order Service"));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> orderPaymentSyncService.syncFromOrder("PAY-1")).getStatusCode());
        verifyNoInteractions(transactionService);
    }
}
