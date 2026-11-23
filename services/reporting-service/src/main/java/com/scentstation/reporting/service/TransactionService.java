
package com.scentstation.reporting.service;

import com.scentstation.reporting.dto.TransactionRequest;
import com.scentstation.reporting.dto.TransactionResponse;
import com.scentstation.reporting.dto.TransactionSyncRequest;
import com.scentstation.reporting.entity.Transaction;
import com.scentstation.reporting.repository.TransactionRepository;

import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class TransactionService {

    private static final Set<String> VALID_STATUSES =
            Set.of("PENDING", "PAID", "FAILED", "EXPIRED", "REFUNDED");

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    // Normalize timestamps to match MySQL DATETIME(0).
    // MySQL rounds fractional seconds when storing DATETIME values.
    private LocalDateTime normalizeToSecond(LocalDateTime value) {
        if (value == null) {
            return null;
        }

        return value.plusNanos(500_000_000)
                .truncatedTo(ChronoUnit.SECONDS);
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
        transaction.setCurrency(
                request.currency().trim().toUpperCase(Locale.ROOT)
        );
        transaction.setStatus(status);
        applyScope(transaction, request.tenantId(), request.machineId(), request.slotId(), request.productId());

        if ("PAID".equals(status)) {
            transaction.setPaidAt(
                    normalizeToSecond(LocalDateTime.now())
            );
        }

        Transaction savedTransaction =
                transactionRepository.save(transaction);

        return toResponse(savedTransaction);
    }

    @Transactional
    public TransactionResponse syncTransaction(TransactionSyncRequest request) {
        TransactionSyncValidator.validate(request, HttpStatus.BAD_REQUEST);
        String reference = request.reference().trim();
        String orderId = request.orderId().trim();
        String currency = request.currency()
                .trim()
                .toUpperCase(Locale.ROOT);
        String status = request.status()
                .trim()
                .toUpperCase(Locale.ROOT);

        // Normalize timestamps for MySQL DATETIME(0).
        LocalDateTime normalizedCreatedAt =
                normalizeToSecond(request.createdAt());

        LocalDateTime normalizedPaidAt =
                normalizeToSecond(request.paidAt());

        Transaction transaction = transactionRepository
                .findByReferenceForUpdate(reference)
                .orElse(null);

        // Case 1: Create a new transaction.
        if (transaction == null) {
            transaction = new Transaction();

            transaction.setReference(reference);
            transaction.setOrderId(orderId);
            transaction.setAmount(request.amount());
            transaction.setCurrency(currency);
            transaction.setStatus(status);
            transaction.setCreatedAt(normalizedCreatedAt);
            transaction.setPaidAt(normalizedPaidAt);
            applyScope(transaction, request.tenantId(), request.machineId(), request.slotId(), request.productId());

            try {
                return toResponse(transactionRepository.saveAndFlush(transaction));
            } catch (DataIntegrityViolationException ex) {
                // A concurrent first delivery may have inserted the unique reference.
                // Roll back this attempt; the caller can safely replay the same payment.
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Transaction reference was inserted concurrently; retry synchronization");
            }
        }

        // Case 2: Reject conflicting transaction data.
        if (!Objects.equals(normalizeToSecond(transaction.getCreatedAt()), normalizedCreatedAt)
                || !transaction.getOrderId().equals(orderId)
                || transaction.getAmount()
                        .compareTo(request.amount()) != 0
                || !transaction.getCurrency()
                        .equalsIgnoreCase(currency)
                || conflicts(transaction.getTenantId(), request.tenantId())
                || conflicts(transaction.getMachineId(), request.machineId())
                || conflicts(transaction.getSlotId(), request.slotId())
                || conflicts(transaction.getProductId(), request.productId())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transaction data conflicts with existing reference"
            );
        }

        boolean scopeEnriched = applyScope(transaction, request.tenantId(), request.machineId(),
                request.slotId(), request.productId());

        // Case 3: Handle an already-paid transaction.
        if ("PAID".equals(transaction.getStatus())) {

            LocalDateTime existingPaidAt =
                    normalizeToSecond(transaction.getPaidAt());

            if ("REFUNDED".equals(status)) {
                if (normalizedPaidAt != null && !Objects.equals(existingPaidAt, normalizedPaidAt)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Paid transaction cannot be changed"
                    );
                }
                transaction.setStatus("REFUNDED");
                return toResponse(transactionRepository.save(transaction));
            }

            if (!"PAID".equals(status)
                    || !Objects.equals(
                            existingPaidAt,
                            normalizedPaidAt
                    )) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Paid transaction cannot be changed"
                );
            }

            // Idempotent synchronization.
            return toResponse(scopeEnriched ? transactionRepository.save(transaction) : transaction);
        }

        // Case 4: Protect terminal transaction statuses.
        if (!"PENDING".equals(transaction.getStatus())) {

            if (!transaction.getStatus().equals(status)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Terminal transaction status cannot be changed"
                );
            }

            return toResponse(scopeEnriched ? transactionRepository.save(transaction) : transaction);
        }

        if ("PENDING".equals(status)) {
            return toResponse(scopeEnriched ? transactionRepository.save(transaction) : transaction);
        }

        // Case 5: Update a pending transaction.
        transaction.setStatus(status);
        transaction.setPaidAt(normalizedPaidAt);

        return toResponse(
                transactionRepository.save(transaction)
        );
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
                transaction.getPaidAt(),
                transaction.getTenantId(),
                transaction.getMachineId(),
                transaction.getSlotId(),
                transaction.getProductId()
        );
    }

    private boolean applyScope(Transaction transaction, String tenantId, String machineId,
            String slotId, Long productId) {
        boolean changed = false;
        if (transaction.getTenantId() == null && tenantId != null && !tenantId.isBlank()) {
            transaction.setTenantId(tenantId.trim()); changed = true;
        }
        if (transaction.getMachineId() == null && machineId != null && !machineId.isBlank()) {
            transaction.setMachineId(machineId.trim()); changed = true;
        }
        if (transaction.getSlotId() == null && slotId != null && !slotId.isBlank()) {
            transaction.setSlotId(slotId.trim()); changed = true;
        }
        if (transaction.getProductId() == null && productId != null) {
            transaction.setProductId(productId); changed = true;
        }
        return changed;
    }

    private boolean conflicts(Object existing, Object incoming) {
        return existing != null && incoming != null && !Objects.equals(existing, incoming);
    }
}
