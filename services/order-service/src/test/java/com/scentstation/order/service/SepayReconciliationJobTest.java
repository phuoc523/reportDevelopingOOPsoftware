package com.scentstation.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SepayReconciliationJobTest {
    @Test
    void importsOnlyNewIncomingCreditsForConfiguredAccountAndPagesAllResults() {
        SepaySettings settings = mock(SepaySettings.class);
        when(settings.enabled()).thenReturn(true);
        when(settings.account()).thenReturn("9999999999");
        SepayReconciliationClient client = mock(SepayReconciliationClient.class);
        SepaySettlement settlement = mock(SepaySettlement.class);
        when(settlement.hasRecordedEvent(any())).thenReturn(false);
        when(client.fetch(any(), any(), eq(1))).thenReturn(new SepayReconciliationClient.Page(List.of(
                transaction("uuid-1", "in", "9999999999", "125.00"),
                transaction("uuid-out", "out", "9999999999", "125.00")),
                new SepayReconciliationClient.Meta(new SepayReconciliationClient.Pagination(2, true))));
        when(client.fetch(any(), any(), eq(2))).thenReturn(new SepayReconciliationClient.Page(List.of(
                transaction("uuid-other-account", "in", "1111111111", "125.00"),
                transaction("uuid-2", "in", "9999999999", "250.00")),
                new SepayReconciliationClient.Meta(new SepayReconciliationClient.Pagination(2, false))));

        var job = new SepayReconciliationJob(settings, client, settlement, new ObjectMapper());
        job.reconcile(LocalDateTime.now().minusHours(24), LocalDateTime.now());

        verify(settlement).accept(eq(transaction("uuid-1", "in", "9999999999", "125.00").toCredit()), any());
        verify(settlement).accept(eq(transaction("uuid-2", "in", "9999999999", "250.00").toCredit()), any());
        verify(settlement, never()).accept(eq(transaction("uuid-out", "out", "9999999999", "125.00").toCredit()), any());
        verify(settlement, never()).accept(eq(transaction("uuid-other-account", "in", "1111111111", "125.00").toCredit()), any());
        verify(client).fetch(any(), any(), eq(2));
    }

    private static SepayReconciliationClient.Transaction transaction(String id, String direction,
                                                                     String account, String amount) {
        return new SepayReconciliationClient.Transaction(id, "2026-11-22 10:00:00", account,
                direction, new BigDecimal(amount), "SS0123456789ABCDEF0123", "FT-" + id,
                "SS0123456789ABCDEF0123", "MBBank");
    }
}
