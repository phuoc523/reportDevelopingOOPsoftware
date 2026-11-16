package com.scentstation.order.repository;

import com.scentstation.order.entity.DispensingCommand;
import com.scentstation.order.entity.DispensingStatus;
import com.scentstation.order.entity.Order;
import com.scentstation.order.entity.OrderItem;
import com.scentstation.order.entity.OrderStatus;
import com.scentstation.order.entity.Payment;
import com.scentstation.order.entity.TransactionReview;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class JpaRepositoryIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DispensingRepository dispensingRepository;

    @Autowired
    private TransactionReviewRepository transactionReviewRepository;

    @Test
    void orderAndItemsPersistWithExpectedAuditFields() {
        Order order = new Order(
                "TENANT-001",
                7L,
                "MACHINE-001",
                "SLOT-01",
                List.of(new OrderItem(3L, "Scent Sample", new BigDecimal("25.50"), 2, new BigDecimal("51.00"))),
                new BigDecimal("51.00"),
                "VND",
                LocalDateTime.now().plusMinutes(30)
        );
        order.setStatus(OrderStatus.PENDING);

        Order saved = orderRepository.save(order);

        assertNotNull(saved.getId());
        assertEquals("TENANT-001", saved.getTenantId());
        assertEquals(1, saved.getItems().size());
        assertEquals(new BigDecimal("51.00"), saved.getTotalAmount());

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertEquals(saved.getId(), reloaded.getId());
        assertEquals("SLOT-01", reloaded.getSlotId());
        assertEquals(OrderStatus.PENDING, reloaded.getStatus());
    }

    @Test
    void saveIfAbsentPreventsDuplicatePaymentAndDispensingRecord() {
        Order order = new Order(
                "TENANT-001",
                8L,
                "MACHINE-002",
                "SLOT-02",
                List.of(new OrderItem(10L, "Lotion", new BigDecimal("12.00"), 1, new BigDecimal("12.00"))),
                new BigDecimal("12.00"),
                "VND",
                LocalDateTime.now().plusMinutes(15)
        );
        order.setStatus(OrderStatus.CONFIRMED);
        Order savedOrder = orderRepository.save(order);

        Payment firstPayment = new Payment(
                "PAY-UNIQUE-1",
                savedOrder.getId(),
                new BigDecimal("12.00"),
                "VND",
                "scentstation://pay/PAY-UNIQUE-1",
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(15)
        );
        firstPayment.markPaid();
        paymentRepository.saveIfAbsent(firstPayment);

        Payment duplicatePayment = new Payment(
                "PAY-UNIQUE-2",
                savedOrder.getId(),
                new BigDecimal("12.00"),
                "VND",
                "scentstation://pay/PAY-UNIQUE-2",
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(15)
        );
        Payment storedPayment = paymentRepository.saveIfAbsent(duplicatePayment);

        assertEquals(firstPayment.getReference(), storedPayment.getReference());
        assertEquals(1, paymentRepository.findAll().stream()
                .filter(payment -> payment.getOrderId().equals(savedOrder.getId()))
                .count());

        DispensingCommand firstCommand = new DispensingCommand(
                "CMD-100",
                savedOrder.getId(),
                firstPayment.getReference(),
                "MACHINE-002",
                "SLOT-02"
        );
        dispensingRepository.saveIfAbsent(firstCommand);

        DispensingCommand duplicateCommand = new DispensingCommand(
                "CMD-101",
                savedOrder.getId(),
                firstPayment.getReference(),
                "MACHINE-002",
                "SLOT-02"
        );
        DispensingCommand storedCommand = dispensingRepository.saveIfAbsent(duplicateCommand);

        assertEquals(firstCommand.getCommandId(), storedCommand.getCommandId());
        assertEquals(1, dispensingRepository.findByOrderId(savedOrder.getId()).stream().count());
    }

    @Test
    void transactionReviewCanBeOpenedAndUpdatedWithoutDuplicateRecords() {
        String reference = "PAY-REVIEW-1";

        TransactionReview review = transactionReviewRepository.openOrUpdate(reference, 99L, "Mismatch detected");
        TransactionReview updated = transactionReviewRepository.openOrUpdate(reference, 99L, "Duplicate callback retry");

        assertEquals(reference, review.getReference());
        assertEquals(reference, updated.getReference());
        assertEquals("Mismatch detected; Duplicate callback retry", updated.getReason());
        assertEquals(1, transactionReviewRepository.findAll().stream()
                .filter(item -> item.getReference().equals(reference))
                .count());
    }

    @Test
    void dispensingResultCanBeRecordedAndMarkedForReview() {
        Order order = new Order(
                "TENANT-001",
                9L,
                "MACHINE-003",
                "SLOT-03",
                List.of(new OrderItem(11L, "Mist", new BigDecimal("9.00"), 1, new BigDecimal("9.00"))),
                new BigDecimal("9.00"),
                "VND",
                LocalDateTime.now().plusMinutes(15)
        );
        order.setStatus(OrderStatus.CONFIRMED);
        Order savedOrder = orderRepository.save(order);

        Payment payment = new Payment(
                "PAY-REVIEW-2",
                savedOrder.getId(),
                new BigDecimal("9.00"),
                "VND",
                "scentstation://pay/PAY-REVIEW-2",
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(15)
        );
        payment.markPaid();
        paymentRepository.save(payment);

        DispensingCommand command = new DispensingCommand(
                "CMD-200",
                savedOrder.getId(),
                payment.getReference(),
                "MACHINE-003",
                "SLOT-03"
        );
        command.recordResult(DispensingStatus.UNKNOWN, "Machine service timed out");
        dispensingRepository.save(command);

        assertEquals(DispensingStatus.UNKNOWN, command.getStatus());
        assertEquals("Machine service timed out", command.getManualReviewReason());
    }
}
