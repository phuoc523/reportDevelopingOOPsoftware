package com.scentstation.order.exception;

public class TransactionReviewNotFoundException extends RuntimeException {

    public TransactionReviewNotFoundException(String reference) {
        super("Transaction review not found: " + reference);
    }
}
