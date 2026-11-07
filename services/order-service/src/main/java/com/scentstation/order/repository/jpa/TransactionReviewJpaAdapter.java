package com.scentstation.order.repository.jpa;

import com.scentstation.order.entity.TransactionReview;
import com.scentstation.order.repository.TransactionReviewRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class TransactionReviewJpaAdapter implements TransactionReviewRepository {

    private final SpringDataTransactionReviewRepository repository;

    public TransactionReviewJpaAdapter(SpringDataTransactionReviewRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public TransactionReview openOrUpdate(String reference, Long orderId, String reason) {
        Optional<TransactionReview> existing = repository.findById(reference);
        TransactionReview review = existing.orElseGet(() -> new TransactionReview(reference, orderId, reason));
        existing.ifPresent(value -> value.updateReason(reason));
        return repository.save(review);
    }

    @Override
    public TransactionReview save(TransactionReview review) {
        return repository.save(review);
    }

    @Override
    public Optional<TransactionReview> findByReference(String reference) {
        return repository.findById(reference);
    }

    @Override
    public List<TransactionReview> findAll() {
        return repository.findAllByOrderByCreatedAtAsc();
    }

    @Override
    @Transactional
    public void deleteAll() {
        repository.deleteAll();
    }
}
