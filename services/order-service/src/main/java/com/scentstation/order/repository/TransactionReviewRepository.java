package com.scentstation.order.repository;

import com.scentstation.order.entity.TransactionReview;

import java.util.List;
import java.util.Optional;

public interface TransactionReviewRepository {

    TransactionReview openOrUpdate(String reference, Long orderId, String reason);

    TransactionReview save(TransactionReview review);

    Optional<TransactionReview> findByReference(String reference);

    List<TransactionReview> findAll();

    void deleteAll();
}
