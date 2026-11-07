
package com.scentstation.reporting.service;

import com.scentstation.reporting.dto.TransactionRequest;
import com.scentstation.reporting.dto.TransactionResponse;
import com.scentstation.reporting.entity.Transaction;
import com.scentstation.reporting.repository.TransactionRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
public class TransactionService {

    private static final Set<String> VALID_STATUSES =
            Set.of("PENDING", "PAID", "FAILED", "EXPIRED");

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request) {

        String reference = request.reference().trim();

        if (transactionRepository.findByReference(reference).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transaction reference already exists"
            );
        }

        String status = request.status()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!VALID_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid transaction status"
            );
        }

        Transaction transaction = new Transaction();

        transaction.setReference(reference);
        transaction.setOrderId(request.orderId().trim());
        transaction.setAmount(request.amount());
        transaction.setCurrency(request.currency().trim().toUpperCase(Locale.ROOT));
        transaction.setStatus(status);

        if ("PAID".equals(status)) {
            transaction.setPaidAt(LocalDateTime.now());
        }

        Transaction savedTransaction = transactionRepository.save(transaction);

        return toResponse(savedTransaction);
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getReference(),
                transaction.getOrderId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus(),
                transaction.getCreatedAt(),
                transaction.getPaidAt()
        );
    }
}

