package com.scentstation.reporting.service;

import com.scentstation.reporting.dto.TransactionFilterRequest;
import com.scentstation.reporting.dto.TransactionResponse;
import com.scentstation.reporting.entity.Transaction;
import com.scentstation.reporting.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Locale;

@Service
public class TransactionQueryService {
    private final TransactionRepository repository;

    public TransactionQueryService(TransactionRepository repository) {
        this.repository = repository;
    }

    public Page<TransactionResponse> search(TransactionFilterRequest filter,
                                             Pageable pageable,
                                             boolean refundsOnly) {
        validate(filter);
        Specification<Transaction> spec = (root, query, cb) -> cb.conjunction();

        if (hasText(filter.reference())) {
            spec = spec.and((r, q, cb) ->
                    cb.like(cb.lower(r.get("reference")),
                            "%" + filter.reference().trim().toLowerCase(Locale.ROOT) + "%"));
        }
        if (hasText(filter.orderId())) {
            spec = spec.and((r, q, cb) ->
                    cb.like(cb.lower(r.get("orderId")),
                            "%" + filter.orderId().trim().toLowerCase(Locale.ROOT) + "%"));
        }
        String status = refundsOnly ? "REFUNDED" :
                (hasText(filter.status()) ? filter.status().trim().toUpperCase(Locale.ROOT) : null);
        if (status != null && !java.util.Set.of("PENDING", "PAID", "FAILED", "EXPIRED", "REFUNDED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid transaction status");
        }
        if (status != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        }
        if (filter.startDate() != null) {
            spec = spec.and((r, q, cb) ->
                    cb.greaterThanOrEqualTo(r.get("createdAt"), filter.startDate().atStartOfDay()));
        }
        if (filter.endDate() != null) {
            spec = spec.and((r, q, cb) ->
                    cb.lessThan(r.get("createdAt"), filter.endDate().plusDays(1).atStartOfDay()));
        }
        if (filter.minAmount() != null) {
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("amount"), filter.minAmount()));
        }
        if (filter.maxAmount() != null) {
            spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("amount"), filter.maxAmount()));
        }
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    public TransactionResponse findById(Long id) {
        return repository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transaction not found"));
    }

    public TransactionResponse findByReference(String reference) {
        return repository.findByReference(reference).map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transaction not found"));
    }

    private void validate(TransactionFilterRequest f) {
        if (f.startDate() != null && f.endDate() != null && f.startDate().isAfter(f.endDate()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
        if (f.minAmount() != null && f.maxAmount() != null && f.minAmount().compareTo(f.maxAmount()) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minAmount must not exceed maxAmount");
        if (f.minAmount() != null && f.minAmount().signum() < 0 ||
                f.maxAmount() != null && f.maxAmount().signum() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount filters must not be negative");
    }

    private boolean hasText(String s) { return s != null && !s.isBlank(); }

    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(t.getId(), t.getReference(), t.getOrderId(),
                t.getAmount(), t.getCurrency(), t.getStatus(), t.getCreatedAt(), t.getPaidAt());
    }
}

