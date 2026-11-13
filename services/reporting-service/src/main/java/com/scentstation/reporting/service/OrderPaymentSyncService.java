
package com.scentstation.reporting.service;

import com.scentstation.reporting.client.OrderPaymentClient;
import com.scentstation.reporting.dto.OrderPaymentResponse;
import com.scentstation.reporting.dto.TransactionResponse;
import com.scentstation.reporting.dto.TransactionSyncRequest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderPaymentSyncService {

    private final OrderPaymentClient orderPaymentClient;
    private final TransactionService transactionService;

    public OrderPaymentSyncService(
            OrderPaymentClient orderPaymentClient,
            TransactionService transactionService) {

        this.orderPaymentClient = orderPaymentClient;
        this.transactionService = transactionService;
    }

    public TransactionResponse syncFromOrder(String reference) {

        if (reference == null || reference.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment reference is required"
            );
        }

        OrderPaymentResponse payment =
                orderPaymentClient.getPayment(reference.trim());

        if (payment == null || payment.reference() == null
                || !payment.reference().equals(reference.trim())
                || payment.orderId() == null || payment.orderId() <= 0
                || payment.amount() == null
                || payment.currency() == null
                || payment.status() == null
                || payment.createdAt() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Invalid payment data from Order Service"
            );
        }

        TransactionSyncRequest request = new TransactionSyncRequest(
                payment.reference(),
                payment.orderId().toString(),
                payment.amount(),
                payment.currency(),
                payment.status(),
                payment.createdAt(),
                payment.paidAt()
        );

        TransactionSyncValidator.validate(request, HttpStatus.BAD_GATEWAY);
        return transactionService.syncTransaction(request);
    }
}
