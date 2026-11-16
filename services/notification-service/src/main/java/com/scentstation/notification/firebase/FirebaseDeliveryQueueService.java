package com.scentstation.notification.firebase;

import com.scentstation.notification.entity.FirebaseDeliveryJob;
import com.scentstation.notification.repository.FirebaseDeliveryJobRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(prefix = "notification.firebase", name = "enabled", havingValue = "true")
public class FirebaseDeliveryQueueService {
    private static final Logger log = LoggerFactory.getLogger(FirebaseDeliveryQueueService.class);
    private static final List<FirebaseDeliveryJob.Status> READY_STATUSES = List.of(
        FirebaseDeliveryJob.Status.PENDING,
        FirebaseDeliveryJob.Status.RETRYING,
        FirebaseDeliveryJob.Status.PROCESSING);

    private final FirebaseDeliveryJobRepository jobs;
    private final FirebaseNotificationDeliveryService delivery;
    private final TransactionTemplate transaction;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration lease;

    public FirebaseDeliveryQueueService(
            FirebaseDeliveryJobRepository jobs,
            FirebaseNotificationDeliveryService delivery,
            PlatformTransactionManager transactionManager,
            @Value("${notification.firebase.delivery.batch-size:20}") int batchSize,
            @Value("${notification.firebase.delivery.max-attempts:5}") int maxAttempts,
            @Value("${notification.firebase.delivery.initial-backoff:10s}") Duration initialBackoff,
            @Value("${notification.firebase.delivery.lease:5m}") Duration lease) {
        this.jobs = jobs;
        this.delivery = delivery;
        this.batchSize = Math.max(1, batchSize);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoff = initialBackoff;
        this.lease = lease;
        transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void enqueue(NotificationCreatedEvent event) {
        jobs.save(new FirebaseDeliveryJob(event, Instant.now()));
    }

    @Scheduled(fixedDelayString = "${notification.firebase.delivery.poll-interval:5s}")
    public void processReady() {
        for (int index = 0; index < batchSize; index++) {
            Optional<ClaimedJob> claimed = claim();
            if (claimed.isEmpty()) return;
            deliver(claimed.get());
        }
    }

    private Optional<ClaimedJob> claim() {
        Optional<ClaimedJob> result = transaction.execute(status -> {
            Instant now = Instant.now();
            List<FirebaseDeliveryJob> ready = jobs.findReady(now, READY_STATUSES, PageRequest.of(0, 1));
            if (ready.isEmpty()) return Optional.empty();
            FirebaseDeliveryJob job = ready.getFirst();
            job.claim(now, now.plus(lease));
            return Optional.of(new ClaimedJob(job.getId(), job.getAttempts(), job.event(), job.getRetryTokens()));
        });
        return result == null ? Optional.empty() : result;
    }

    private void deliver(ClaimedJob claimed) {
        try {
            FcmDeliveryResult result = delivery.send(claimed.event(), claimed.retryTokens());
            if (result.requestedCount() == 0) {
                complete(claimed.id(), FirebaseDeliveryJob.Status.SKIPPED, result);
            } else if (result.failureCount() == 0) {
                complete(claimed.id(), FirebaseDeliveryJob.Status.SENT, result);
            } else if (result.failureCount() == result.invalidTokens().size()) {
                complete(claimed.id(), FirebaseDeliveryJob.Status.PARTIAL, result);
            } else {
                retry(claimed, "TransientFcmFailure", result.retryableTokens());
            }
        } catch (FcmDeliveryException exception) {
            retry(claimed, exception.getClass().getSimpleName(), exception.getRetryableTokens());
        } catch (RuntimeException exception) {
            retry(claimed, exception.getClass().getSimpleName(), claimed.retryTokens());
        }
    }

    private void complete(Long id, FirebaseDeliveryJob.Status outcome, FcmDeliveryResult result) {
        transaction.executeWithoutResult(status -> jobs.findById(id).ifPresent(job ->
            job.complete(outcome, result.requestedCount(), result.successCount(), result.failureCount(), Instant.now())));
    }

    private void retry(ClaimedJob claimed, String errorType, List<String> retryTokens) {
        transaction.executeWithoutResult(status -> jobs.findById(claimed.id()).ifPresent(job -> {
            Instant now = Instant.now();
            long multiplier = 1L << Math.min(Math.max(0, claimed.attempts() - 1), 10);
            job.retryOrFail(errorType, retryTokens, now,
                now.plus(initialBackoff.multipliedBy(multiplier)), maxAttempts);
            log.warn("FCM delivery deferred notificationId={} attempt={} errorType={}",
                claimed.event().notificationId(), claimed.attempts(), errorType);
        }));
    }

    private record ClaimedJob(Long id, int attempts, NotificationCreatedEvent event,
                              List<String> retryTokens) {}
}
