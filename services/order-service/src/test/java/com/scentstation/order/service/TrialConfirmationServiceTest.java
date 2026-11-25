package com.scentstation.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.scentstation.order.service.TrialConfirmationService.Status;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TrialConfirmationServiceTest {
    @Test
    void createsSessionAndTransitionsToConfirmedOrDeclined() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-25T16:00:00Z"), ZoneOffset.UTC);
        TrialConfirmationService service = new TrialConfirmationService("http://192.168.1.20:3000/", clock);

        var created = service.create("9101", "91001", 91001L, "Signature No. 01",
                new BigDecimal("39600"), "VND");
        assertThat(created.status()).isEqualTo(Status.PENDING);
        assertThat(created.confirmUrl()).isEqualTo("http://192.168.1.20:3000/?confirmSession=" + created.sessionId());

        var confirmed = service.respond(created.sessionId(), true);
        assertThat(confirmed.status()).isEqualTo(Status.CONFIRMED);
        assertThat(service.get(created.sessionId()).status()).isEqualTo(Status.CONFIRMED);

        var second = service.create("9101", "91006", 91006L, "Citrus Veil",
                new BigDecimal("39000"), "VND");
        var declined = service.respond(second.sessionId(), false);
        assertThat(declined.status()).isEqualTo(Status.DECLINED);
    }

    @Test
    void rejectsUnknownSession() {
        TrialConfirmationService service = new TrialConfirmationService("http://192.168.1.20:3000");
        assertThatThrownBy(() -> service.get("missing-session"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
