package com.scentstation.order.repository;

import com.scentstation.order.entity.TransactionReview;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryTransactionReviewRepository implements TransactionReviewRepository {

    private final ConcurrentMap<String, TransactionReview> reviews = new ConcurrentHashMap<>();

    @Override
    public TransactionReview openOrUpdate(String reference, Long orderId, String reason) {
        return reviews.compute(reference, (key, existing) -> {
            if (existing == null) {
                return new TransactionReview(reference, orderId, reason);
            }
            existing.updateReason(reason);
            return existing;
        });
    }

    @Override
    public Optional<TransactionReview> findByReference(String reference) {
        return Optional.ofNullable(reviews.get(reference));
    }

    @Override
    public List<TransactionReview> findAll() {
        return reviews.values()
                .stream()
                .sorted(Comparator.comparing(TransactionReview::getCreatedAt))
                .toList();
    }

    @Override
    public void deleteAll() {
        reviews.clear();
    }
}
