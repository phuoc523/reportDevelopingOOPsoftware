package com.scentstation.order.service;

import com.scentstation.order.entity.OrderStatus;
import com.scentstation.order.entity.OrderType;
import com.scentstation.order.entity.PaymentStatus;
import com.scentstation.order.repository.PaymentRepository;
import com.scentstation.order.repository.jpa.SpringDataOrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;

@Service
public class SepaySettlement {
    private static final ZoneId BANK_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter BANK_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    private final SepaySettings settings;
    private final JdbcTemplate jdbc;
    private final SpringDataOrderRepository orders;
    private final PaymentRepository payments;
    private final ApplicationEventPublisher events;
    private final DispensingService dispensing;

    public SepaySettlement(SepaySettings settings, JdbcTemplate jdbc, SpringDataOrderRepository orders,
                           PaymentRepository payments, ApplicationEventPublisher events, DispensingService dispensing) {
        this.settings = settings;
        this.jdbc = jdbc;
        this.orders = orders;
        this.payments = payments;
        this.events = events;
        this.dispensing = dispensing;
    }

    @Transactional
    public void accept(SepayCredit credit, String rawPayload) {
        jdbc.update("""
                insert into sepay_credit_events
                (transaction_id, account_number, bank_reference, transfer_code, transfer_content,
                 amount, direction, gateway, transaction_date, raw_payload, outcome, received_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RECEIVED', ?)
                """, credit.id(), credit.accountNumber(), bankReference(credit), credit.code(),
                credit.content(), credit.transferAmount(), credit.transferType(), credit.gateway(),
                credit.transactionDate(), rawPayload, LocalDateTime.now());
        String outcome = settle(credit);
        jdbc.update("update sepay_credit_events set outcome = ? where transaction_id = ?", outcome, credit.id());
    }

    public boolean hasRecordedEvent(SepayCredit credit) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select count(*) > 0 from sepay_credit_events
                where transaction_id = ? or (account_number = ? and bank_reference = ?)
                """, Boolean.class, credit.id(), credit.accountNumber(), bankReference(credit)));
    }

    private String settle(SepayCredit credit) {
        if (!"in".equals(credit.transferType()) || credit.transferAmount().signum() <= 0
                || !settings.account().equals(credit.accountNumber())
                || !("MB".equalsIgnoreCase(credit.gateway()) || "MBBank".equalsIgnoreCase(credit.gateway()))
                || bankReference(credit) == null) {
            return "UNMATCHED_TRANSFER";
        }
        String code = Objects.toString(credit.code(), "");
        String content = Objects.toString(credit.content(), "").strip();
        String reference = code.isBlank() ? content : code;
        if (!SepaySettings.isSepayReference(reference)
                || !Arrays.asList(content.split("\\s+")).contains(reference)) {
            return "UNMATCHED_REFERENCE";
        }

        var orderIds = jdbc.queryForList("select order_id from payments where reference = ?", Long.class, reference);
        if (orderIds.isEmpty()) return "UNMATCHED_REFERENCE";
        long orderId = orderIds.getFirst();
        var order = orders.findByIdForUpdate(orderId).orElseThrow();
        var payment = payments.findByReference(reference).orElseThrow();
        if (order.getOrderType() != OrderType.TRIAL || order.getStatus() != OrderStatus.PENDING
                || payment.getStatus() != PaymentStatus.PENDING) return "ORDER_NOT_PENDING";
        if (!"VND".equals(payment.getCurrency())
                || payment.getAmount().compareTo(credit.transferAmount()) != 0
                || order.getTotalAmount().compareTo(payment.getAmount()) != 0) return "AMOUNT_MISMATCH";
        // The stored image URL includes the account used at checkout. Never apply a changed account to an old payment.
        if (!payment.getQrPayload().equals(settings.qrImage(reference, payment.getAmount()))) return "ACCOUNT_CHANGED";

        var now = LocalDateTime.now();
        if (!now.isBefore(payment.getExpiresAt()) || !now.isBefore(order.getExpiresAt())) return "EXPIRED";
        try {
            var bankTime = LocalDateTime.parse(credit.transactionDate(), BANK_TIME).atZone(BANK_ZONE).toInstant();
            var createdAt = payment.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant();
            if (bankTime.isBefore(createdAt.truncatedTo(ChronoUnit.SECONDS))
                    || bankTime.isAfter(now.atZone(ZoneId.systemDefault()).toInstant())) return "INVALID_BANK_TIME";
        } catch (DateTimeParseException exception) {
            return "INVALID_BANK_TIME";
        }

        order.setStatus(OrderStatus.CONFIRMED);
        orders.save(order);
        payment.markPaid();
        payments.save(payment);
        dispensing.prepareConfirmedTrial(order, payment);
        events.publishEvent(new ConfirmedTrial(orderId));
        return "PAID";
    }

    private static String bankReference(SepayCredit credit) {
        return credit.referenceCode() == null || credit.referenceCode().isBlank()
                ? null : credit.referenceCode().strip();
    }

    public record ConfirmedTrial(long orderId) {}
}
