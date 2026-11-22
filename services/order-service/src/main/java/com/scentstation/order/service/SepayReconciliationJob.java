package com.scentstation.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "payment.sepay.reconciliation", name = "enabled", havingValue = "true")
public class SepayReconciliationJob {
    private static final Logger log = LoggerFactory.getLogger(SepayReconciliationJob.class);
    private static final int MAX_PAGES = 1000;
    private final SepaySettings settings;
    private final SepayReconciliationClient client;
    private final SepaySettlement settlement;
    private final ObjectMapper mapper;

    public SepayReconciliationJob(SepaySettings settings, SepayReconciliationClient client,
                                  SepaySettlement settlement, ObjectMapper mapper) {
        this.settings = settings;
        this.client = client;
        this.settlement = settlement;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${payment.sepay.reconciliation.interval-ms:900000}", initialDelay = 30000)
    public void reconcileRecentTransactions() {
        if (!settings.enabled()) return;
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusHours(24);
        try {
            reconcile(from, to);
        } catch (RuntimeException exception) {
            // The next scheduled run retries the overlapping 24-hour window. Never log the API token or payload.
            log.error("SePay transaction reconciliation failed; the next scheduled run will retry", exception);
        }
    }

    void reconcile(LocalDateTime from, LocalDateTime to) {
        int lastPage = 1;
        int imported = 0;
        for (int pageNumber = 1; pageNumber <= lastPage; pageNumber++) {
            if (pageNumber > MAX_PAGES) throw new IllegalStateException("SePay reconciliation exceeded the page safety limit");
            var page = Objects.requireNonNull(client.fetch(from, to, pageNumber), "SePay returned an empty response");
            if (page.meta() == null || page.meta().pagination() == null) {
                throw new IllegalStateException("SePay response did not include pagination metadata");
            }
            lastPage = page.meta().pagination().last_page();
            if (lastPage < 1 || lastPage > MAX_PAGES) throw new IllegalStateException("SePay returned an invalid page count");
            if (page.data() == null) continue;
            for (var transaction : page.data()) {
                if (transaction == null || transaction.id() == null || transaction.id().isBlank()
                        || !"in".equalsIgnoreCase(transaction.transfer_type())
                        || transaction.amount_in() == null || transaction.amount_in().signum() <= 0
                        || !settings.account().equals(transaction.account_number())) continue;
                var credit = transaction.toCredit();
                if (settlement.hasRecordedEvent(credit)) continue;
                try {
                    settlement.accept(credit, rawPayload(transaction));
                    imported++;
                } catch (DuplicateKeyException duplicate) {
                    if (!settlement.hasRecordedEvent(credit)) throw duplicate;
                }
            }
            if (pageNumber < lastPage) {
                try {
                    Thread.sleep(350); // SePay permits at most three API requests per second.
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("SePay reconciliation was interrupted", interrupted);
                }
            }
        }
        log.info("SePay transaction reconciliation completed; imported {} previously unseen account credits", imported);
    }

    private String rawPayload(SepayReconciliationClient.Transaction transaction) {
        try {
            return mapper.writeValueAsString(transaction);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize SePay reconciliation transaction", exception);
        }
    }
}
