package com.scentstation.order.service;

import com.scentstation.order.client.MachineCommandRequest;
import com.scentstation.order.client.MachineCommandResponse;
import com.scentstation.order.client.MachineServiceGateway;
import com.scentstation.order.dto.response.DispensingResponse;
import com.scentstation.order.entity.DispensingCommand;
import com.scentstation.order.entity.DispensingStatus;
import com.scentstation.order.entity.Order;
import com.scentstation.order.entity.OrderItem;
import com.scentstation.order.entity.OrderStatus;
import com.scentstation.order.entity.Payment;
import com.scentstation.order.entity.TransactionReview;
import com.scentstation.order.exception.PaymentNotAvailableException;
import com.scentstation.order.repository.DispensingRepository;
import com.scentstation.order.repository.OrderRepository;
import com.scentstation.order.repository.PaymentRepository;
import com.scentstation.order.repository.TransactionReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

class DispensingServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final DispensingRepository commands = mock(DispensingRepository.class);
    private final FakeMachineServiceGateway machineServiceClient = new FakeMachineServiceGateway();
    private final TransactionReviewRepository reviews = mock(TransactionReviewRepository.class);
    private final AtomicReference<DispensingCommand> storedCommand = new AtomicReference<>();
    private final DispensingService dispensingService = new DispensingService(
            orders, payments, commands, machineServiceClient, reviews
    );

    private Order order;

    @BeforeEach
    void setUp() {
        reset(orders, payments, commands, reviews);
        storedCommand.set(null);
        machineServiceClient.reset();
        order = new Order(
                7L,
                "MACHINE-001",
                "SLOT-01",
                List.of(new OrderItem(3L, "Scent Sample", new BigDecimal("25.50"), 1, new BigDecimal("25.50"))),
                new BigDecimal("25.50")
        );
        order.setId(1L);
        order.setStatus(OrderStatus.CONFIRMED);
        when(orders.findById(1L)).thenReturn(Optional.of(order));
        Payment payment = new Payment(
                "PAY-001",
                order.getId(),
                new BigDecimal("25.50"),
                "scentstation://pay/PAY-001"
        );
        payment.markPaid();
        when(payments.findByOrderId(1L)).thenReturn(Optional.of(payment));
        when(commands.findByOrderId(1L)).thenAnswer(invocation -> Optional.ofNullable(storedCommand.get()));
        when(commands.saveIfAbsent(any())).thenAnswer(invocation -> {
            DispensingCommand command = invocation.getArgument(0);
            storedCommand.compareAndSet(null, command);
            return storedCommand.get();
        });
        when(commands.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionReview review = new TransactionReview("PAY-001", order.getId(), "Machine response");
        when(reviews.openOrUpdate(anyString(), anyLong(), anyString())).thenReturn(review);
        when(reviews.findByReference("PAY-001")).thenReturn(Optional.of(review));
    }

    @Test
    void acceptedCommandDoesNotMarkOrderDispensedUntilMachineConfirmsSuccess() {
        machineServiceClient.submitResponse = request -> response(request, "ACCEPTED");

        DispensingResponse accepted = dispensingService.requestDispensing(order.getId());

        assertEquals(DispensingStatus.ACCEPTED, accepted.status());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(0, machineServiceClient.lookupCount);
    }

    @Test
    void confirmedMachineSuccessMarksOrderDispensed() {
        machineServiceClient.submitResponse = request -> response(request, "ACCEPTED");
        DispensingResponse accepted = dispensingService.requestDispensing(order.getId());
        machineServiceClient.resultResponse = new MachineCommandResponse(
                order.getId(), accepted.commandId(), "DISPENSED", "Done"
        );

        DispensingResponse result = dispensingService.getDispensingResult(order.getId());

        assertEquals(DispensingStatus.DISPENSED, result.status());
        assertEquals(OrderStatus.DISPENSED, order.getStatus());
        assertFalse(result.manualReviewRequired());
    }

    @Test
    void machineFailureIsRecordedWithoutRetrying() {
        machineServiceClient.submitResponse = request -> response(request, "FAILED");

        DispensingResponse result = dispensingService.requestDispensing(order.getId());

        assertEquals(DispensingStatus.FAILED, result.status());
        assertEquals(OrderStatus.FAILED, order.getStatus());
        assertTrue(result.manualReviewRequired());
        assertEquals("Machine response", reviews.findByReference("PAY-001").orElseThrow().getReason());
        assertEquals(result.commandId(), dispensingService.requestDispensing(order.getId()).commandId());
        assertEquals(1, machineServiceClient.submitCount);
    }

    @Test
    void timeoutIsManualReviewAndDoesNotCreateASecondCommand() {
        machineServiceClient.submitFailure = new ResourceAccessException("Read timed out");

        DispensingResponse uncertain = dispensingService.requestDispensing(order.getId());
        DispensingResponse repeated = dispensingService.requestDispensing(order.getId());

        assertEquals(DispensingStatus.UNKNOWN, uncertain.status());
        assertTrue(uncertain.manualReviewRequired());
        assertEquals(uncertain.commandId(), repeated.commandId());
        assertEquals(1, machineServiceClient.submitCount);
    }

    @Test
    void doesNotSendCommandBeforePaymentIsPaid() {
        when(payments.findByOrderId(1L)).thenReturn(Optional.of(new Payment(
                "PAY-PENDING",
                order.getId(),
                new BigDecimal("25.50"),
                "scentstation://pay/PAY-PENDING"
        )));

        assertThrows(
                PaymentNotAvailableException.class,
                () -> dispensingService.requestDispensing(order.getId())
        );

        assertEquals(0, machineServiceClient.submitCount);
    }

    private MachineCommandResponse response(MachineCommandRequest request, String status) {
        return new MachineCommandResponse(request.orderId(), request.commandId(), status, "Machine response");
    }

    private static class FakeMachineServiceGateway implements MachineServiceGateway {

        private Function<MachineCommandRequest, MachineCommandResponse> submitResponse = request -> null;
        private ResourceAccessException submitFailure;
        private MachineCommandResponse resultResponse;
        private int submitCount;
        private int lookupCount;

        @Override
        public MachineCommandResponse submit(MachineCommandRequest request) {
            submitCount++;
            if (submitFailure != null) {
                throw submitFailure;
            }
            return submitResponse.apply(request);
        }

        @Override
        public MachineCommandResponse getResult(String commandId) {
            lookupCount++;
            return resultResponse;
        }

        private void reset() {
            submitResponse = request -> null;
            submitFailure = null;
            resultResponse = null;
            submitCount = 0;
            lookupCount = 0;
        }
    }
}
