
package com.scentstation.reporting.service;

import com.scentstation.reporting.dto.TransactionSyncRequest;
import com.scentstation.reporting.dto.TransactionResponse;
import com.scentstation.reporting.entity.Transaction;
import com.scentstation.reporting.repository.TransactionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    private Transaction existingTransaction;

    private final String reference = "PAY-TEST-001";

    private final LocalDateTime createdAt =
            LocalDateTime.of(2026, 11, 13, 11, 25, 6);

    private final LocalDateTime paidAt =
            LocalDateTime.of(2026, 11, 13, 11, 26, 46);

    @BeforeEach
    void setUp() {
        existingTransaction = new Transaction();
        existingTransaction.setId(8L);
        existingTransaction.setReference(reference);
        existingTransaction.setOrderId("2");
        existingTransaction.setAmount(new BigDecimal("25.50"));
        existingTransaction.setCurrency("VND");
        existingTransaction.setStatus("PAID");
        existingTransaction.setCreatedAt(createdAt);
        existingTransaction.setPaidAt(paidAt);
    }

    private TransactionSyncRequest createRequest(
            String status,
            BigDecimal amount,
            LocalDateTime requestPaidAt
    ) {
        return new TransactionSyncRequest(
                reference,
                "2",
                amount,
                "VND",
                status,
                createdAt,
                requestPaidAt
        );
    }

    @Test
    void syncPaidTransactionWithSameDataShouldSucceed() {

        when(transactionRepository.findByReferenceForUpdate(reference))
                .thenReturn(Optional.of(existingTransaction));

        TransactionSyncRequest request = createRequest(
                "PAID",
                new BigDecimal("25.50"),
                paidAt
        );

        TransactionResponse response =
                transactionService.syncTransaction(request);

        assertEquals(8L, response.id());
        assertEquals("PAID", response.status());

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void syncPaidTransactionWithPendingStatusShouldReturn409() {

        when(transactionRepository.findByReferenceForUpdate(reference))
                .thenReturn(Optional.of(existingTransaction));

        TransactionSyncRequest request = createRequest(
                "PENDING",
                new BigDecimal("25.50"),
                null
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> transactionService.syncTransaction(request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void syncPaidTransactionWithDifferentAmountShouldReturn409() {

        when(transactionRepository.findByReferenceForUpdate(reference))
                .thenReturn(Optional.of(existingTransaction));

        TransactionSyncRequest request = createRequest(
                "PAID",
                new BigDecimal("50.00"),
                paidAt
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> transactionService.syncTransaction(request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void syncPaidTransactionWithDifferentPaidAtShouldReturn409() {

        when(transactionRepository.findByReferenceForUpdate(reference))
                .thenReturn(Optional.of(existingTransaction));

        TransactionSyncRequest request = createRequest(
                "PAID",
                new BigDecimal("25.50"),
                paidAt.plusMinutes(5)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> transactionService.syncTransaction(request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void syncPaidTransactionWithFractionalSecondsShouldSucceed() {

        when(transactionRepository.findByReferenceForUpdate(reference))
                .thenReturn(Optional.of(existingTransaction));

        LocalDateTime fractionalPaidAt =
                LocalDateTime.of(
                        2026, 11, 13, 11, 26, 45, 529691100
                );

        TransactionSyncRequest request = createRequest(
                "PAID",
                new BigDecimal("25.50"),
                fractionalPaidAt
        );

        TransactionResponse response =
                transactionService.syncTransaction(request);

        assertEquals(8L, response.id());
        assertEquals("PAID", response.status());

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void newPendingIsCreatedAndDuplicateDoesNotWriteAgain() {
        when(transactionRepository.findByReferenceForUpdate(reference)).thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Transaction saved = invocation.getArgument(0);
            saved.setId(9L);
            return saved;
        });
        var request = createRequest("PENDING", new BigDecimal("25.50"), null);
        var created = transactionService.syncTransaction(request);
        assertEquals("PENDING", created.status());
        assertNull(created.paidAt());
        var captor = org.mockito.ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).saveAndFlush(captor.capture());
        when(transactionRepository.findByReferenceForUpdate(reference))
                .thenReturn(Optional.of(captor.getValue()));
        assertEquals(created, transactionService.syncTransaction(request));
        verify(transactionRepository, times(1)).saveAndFlush(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void pendingTransitionsToPaidOnTheSameRow() {
        existingTransaction.setStatus("PENDING");
        existingTransaction.setPaidAt(null);
        when(transactionRepository.findByReferenceForUpdate(reference)).thenReturn(Optional.of(existingTransaction));
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = transactionService.syncTransaction(createRequest("PAID", new BigDecimal("25.50"), paidAt));
        assertEquals(8L, response.id());
        assertEquals("PAID", response.status());
        assertEquals(paidAt, response.paidAt());
    }

    @Test
    void originalCreationTimeCannotBeReplaced() {
        when(transactionRepository.findByReferenceForUpdate(reference)).thenReturn(Optional.of(existingTransaction));
        var request = new TransactionSyncRequest(reference, "2", new BigDecimal("25.50"), "VND",
                "PAID", createdAt.minusDays(1), paidAt);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> transactionService.syncTransaction(request)).getStatusCode());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void concurrentInsertReturnsConflictInsteadOfAnInternalError() {
        when(transactionRepository.findByReferenceForUpdate(reference)).thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> transactionService.syncTransaction(createRequest("PENDING", new BigDecimal("25.50"), null)))
                .getStatusCode());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"FAILED", "EXPIRED", "REFUNDED"})
    void terminalStatusCannotBeOverwrittenByPending(String status) {
        existingTransaction.setStatus(status);
        existingTransaction.setPaidAt(null);
        when(transactionRepository.findByReferenceForUpdate(reference)).thenReturn(Optional.of(existingTransaction));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> transactionService.syncTransaction(createRequest("PENDING", new BigDecimal("25.50"), null)))
                .getStatusCode());
        assertEquals(status, existingTransaction.getStatus());
        verify(transactionRepository, never()).save(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"-1", "0", "0.001", "25.501", "10000000000000"})
    void invalidAmountsAreRejectedBeforeDatabaseAccess(String amount) {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> transactionService.syncTransaction(createRequest("PENDING", new BigDecimal(amount), null)))
                .getStatusCode());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void missingRequestReturnsBadRequest() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> transactionService.syncTransaction(null)).getStatusCode());
        verifyNoInteractions(transactionRepository);
    }
}
