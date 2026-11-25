package com.scentstation.order.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TrialConfirmationService {
    public enum Status {
        PENDING,
        CONFIRMED,
        DECLINED,
        EXPIRED
    }

    public record TrialConfirmationResponse(
            String sessionId,
            Status status,
            String confirmUrl,
            String machineId,
            String slotId,
            Long productId,
            String productName,
            BigDecimal unitPrice,
            String currency,
            Instant expiresAt
    ) {
    }

    private static final class SessionRecord {
        final String sessionId;
        final String machineId;
        final String slotId;
        final Long productId;
        final String productName;
        final BigDecimal unitPrice;
        final String currency;
        final Instant expiresAt;
        volatile Status status;

        SessionRecord(String sessionId, String machineId, String slotId, Long productId,
                      String productName, BigDecimal unitPrice, String currency, Instant expiresAt) {
            this.sessionId = sessionId;
            this.machineId = machineId;
            this.slotId = slotId;
            this.productId = productId;
            this.productName = productName;
            this.unitPrice = unitPrice;
            this.currency = currency;
            this.expiresAt = expiresAt;
            this.status = Status.PENDING;
        }
    }

    private static final Duration SESSION_TTL = Duration.ofMinutes(5);

    private final Map<String, SessionRecord> sessions = new ConcurrentHashMap<>();
    private final String baseUrl;
    private final Clock clock;

    @Autowired
    public TrialConfirmationService(@Value("${payment.mock-web-base-url}") String mockPaymentWebBaseUrl) {
        this(mockPaymentWebBaseUrl, Clock.systemUTC());
    }

    TrialConfirmationService(String mockPaymentWebBaseUrl, Clock clock) {
        String trimmed = mockPaymentWebBaseUrl == null ? "" : mockPaymentWebBaseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        this.baseUrl = trimmed;
        this.clock = clock;
    }

    public TrialConfirmationResponse create(String machineId, String slotId, Long productId,
                                            String productName, BigDecimal unitPrice, String currency) {
        cleanupExpired();
        String sessionId = UUID.randomUUID().toString();
        Instant expiresAt = clock.instant().plus(SESSION_TTL);
        String resolvedCurrency = (currency == null || currency.isBlank()) ? "VND" : currency.trim();
        SessionRecord record = new SessionRecord(
                sessionId, machineId.trim(), slotId.trim(), productId,
                productName.trim(), unitPrice, resolvedCurrency, expiresAt
        );
        sessions.put(sessionId, record);
        return toResponse(record);
    }

    public TrialConfirmationResponse get(String sessionId) {
        SessionRecord record = requireSession(sessionId);
        return toResponse(record);
    }

    public TrialConfirmationResponse respond(String sessionId, boolean accepted) {
        SessionRecord record = requireSession(sessionId);
        synchronized (record) {
            refreshExpiry(record);
            if (record.status == Status.EXPIRED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Phiên xác nhận dùng thử đã hết hạn.");
            }
            if (record.status != Status.PENDING) {
                return toResponse(record);
            }
            record.status = accepted ? Status.CONFIRMED : Status.DECLINED;
            return toResponse(record);
        }
    }

    private SessionRecord requireSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên xác nhận dùng thử.");
        }
        SessionRecord record = sessions.get(sessionId.trim());
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên xác nhận dùng thử.");
        }
        refreshExpiry(record);
        return record;
    }

    private void refreshExpiry(SessionRecord record) {
        if (record.status == Status.PENDING && !clock.instant().isBefore(record.expiresAt)) {
            record.status = Status.EXPIRED;
        }
    }

    private void cleanupExpired() {
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(15));
        sessions.values().removeIf(item -> item.expiresAt.isBefore(cutoff));
    }

    private TrialConfirmationResponse toResponse(SessionRecord record) {
        String confirmUrl = baseUrl + "/?confirmSession=" + record.sessionId;
        return new TrialConfirmationResponse(
                record.sessionId,
                record.status,
                confirmUrl,
                record.machineId,
                record.slotId,
                record.productId,
                record.productName,
                record.unitPrice,
                record.currency,
                record.expiresAt
        );
    }
}
