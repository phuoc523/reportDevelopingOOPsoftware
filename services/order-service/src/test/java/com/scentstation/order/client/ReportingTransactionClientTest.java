package com.scentstation.order.client;

import com.scentstation.order.dto.response.PaymentResponse;
import com.scentstation.order.entity.PaymentStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ReportingTransactionClientTest {
    private final RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
    private final PaymentResponse payment = new PaymentResponse(
            "PAY-TEST", 1L, new BigDecimal("25000"), "VND", PaymentStatus.PENDING,
            "qr", LocalDateTime.of(2026, 11, 13, 10, 0),
            LocalDateTime.of(2026, 11, 13, 10, 15), null);

    private ReportingTransactionClient client(boolean enabled) {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.requestFactory(any())).thenReturn(builder);
        when(builder.build()).thenReturn(restClient);
        return new ReportingTransactionClient(builder, enabled, "http://reporting:8086", "test-key");
    }

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void publishesOnlyAfterCommit() {
        ReportingTransactionClient client = client(true);
        RestClient.RequestBodyUriSpec request = mock(RestClient.RequestBodyUriSpec.class, RETURNS_SELF);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        clearInvocations(restClient);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        client.publish(payment);
        verify(restClient, never()).post();

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(restClient).post();
    }

    @Test
    void rollbackDoesNotPublish() {
        ReportingTransactionClient client = client(true);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        client.publish(payment);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(restClient, never()).post();
    }

    @Test
    void unavailableReportingDoesNotFailPayment() {
        ReportingTransactionClient client = client(true);
        when(restClient.post()).thenThrow(new ResourceAccessException("Connection refused"));
        assertDoesNotThrow(() -> client.publish(payment));
    }

    @Test
    void disabledIntegrationDoesNotSendRequests() {
        client(false).publish(payment);
        verifyNoInteractions(restClient);
    }
}
