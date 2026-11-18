package com.scentstation.reporting.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.scentstation.reporting.dto.TransactionSyncRequest;
import com.scentstation.reporting.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentEventKafkaConsumerTest {
    @Mock
    private TransactionService transactions;

    @Test
    void consumesValidPaymentEventThroughIdempotentProjectionService() {
        LocalDateTime now = LocalDateTime.now();
        PaymentEventKafkaConsumer consumer = new PaymentEventKafkaConsumer(transactions);
        consumer.consume(new PaymentChangedEvent(
            1, "PAY-1:PAID", "PAY-1", 42L, new BigDecimal("25000"), "VND",
            "PAID", now.minusMinutes(1), now, now));

        ArgumentCaptor<TransactionSyncRequest> request = ArgumentCaptor.forClass(TransactionSyncRequest.class);
        verify(transactions).syncTransaction(request.capture());
        assertEquals("PAY-1", request.getValue().reference());
        assertEquals("42", request.getValue().orderId());
        assertEquals("PAID", request.getValue().status());
    }

    @Test
    void rejectsPaidEventWithoutPaidTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        PaymentEventKafkaConsumer consumer = new PaymentEventKafkaConsumer(transactions);
        assertThrows(PaymentEventPayloadException.class, () -> consumer.consume(
            new PaymentChangedEvent(1, "PAY-1:PAID", "PAY-1", 42L,
                BigDecimal.ONE, "VND", "PAID", now, null, now)));
    }
}
