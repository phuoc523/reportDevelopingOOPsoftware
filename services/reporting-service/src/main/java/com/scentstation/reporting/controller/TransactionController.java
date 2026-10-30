package com.scentstation.reporting.controller;

import com.scentstation.reporting.dto.*;
import com.scentstation.reporting.service.TransactionQueryService;
import com.scentstation.reporting.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reporting/transactions")
public class TransactionController {
    private final TransactionService transactionService;
    private final TransactionQueryService queryService;

    public TransactionController(TransactionService transactionService,
                                 TransactionQueryService queryService) {
        this.transactionService = transactionService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.createTransaction(request));
    }

    @GetMapping
    public Page<TransactionResponse> searchTransactions(
        @RequestParam(required = false) String reference,
        @RequestParam(required = false) String orderId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) LocalDate startDate,
        @RequestParam(required = false) LocalDate endDate,
        @RequestParam(required = false) BigDecimal minAmount,
        @RequestParam(required = false) BigDecimal maxAmount,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String direction) {

        if (page < 0 || size < 1 || size > 200) {
        throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "page must be >= 0 and size must be between 1 and 200");
        }

        if (!java.util.Set.of("id", "createdAt", "paidAt", "amount", "reference", "status")
            .contains(sortBy)) {
        throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Unsupported sortBy field");
    }

    Sort sort = "asc".equalsIgnoreCase(direction)
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();

    return queryService.search(
            new TransactionFilterRequest(
                    reference, orderId, status,
                    startDate, endDate, minAmount, maxAmount),
            PageRequest.of(page, size, sort),
            false);
    }

    @GetMapping("/{id}")
    public TransactionResponse getById(@PathVariable Long id) {
        return queryService.findById(id);
    }

    @GetMapping("/reference/{reference}")
    public TransactionResponse getByReference(@PathVariable String reference) {
        return queryService.findByReference(reference);
    }
}
