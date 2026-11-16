package com.scentstation.order.repository.jpa;

import com.scentstation.order.entity.TransactionReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataTransactionReviewRepository extends JpaRepository<TransactionReview, String> {

    List<TransactionReview> findAllByOrderByCreatedAtAsc();
}
