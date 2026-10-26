package com.scentstation.order.service;

import com.scentstation.order.client.MachineCommandRequest;
import com.scentstation.order.client.MachineCommandResponse;
import com.scentstation.order.client.MachineServiceGateway;
import com.scentstation.order.dto.response.DispensingResponse;
import com.scentstation.order.entity.DispensingCommand;
import com.scentstation.order.entity.DispensingStatus;
import com.scentstation.order.entity.Order;
import com.scentstation.order.entity.OrderStatus;
import com.scentstation.order.entity.Payment;
import com.scentstation.order.entity.PaymentStatus;
import com.scentstation.order.exception.OrderNotFoundException;
import com.scentstation.order.exception.PaymentNotAvailableException;
import com.scentstation.order.repository.DispensingRepository;
import com.scentstation.order.repository.OrderRepository;
import com.scentstation.order.repository.PaymentRepository;
import com.scentstation.order.repository.TransactionReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.Locale;
import java.util.UUID;

@Service
public class DispensingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DispensingService.class);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final DispensingRepository dispensingRepository;
    private final MachineServiceGateway machineServiceClient;
    private final TransactionReviewRepository transactionReviewRepository;
    private final String tenantId;

    public DispensingService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            DispensingRepository dispensingRepository,
            MachineServiceGateway machineServiceClient,
            TransactionReviewRepository transactionReviewRepository,
            @Value("${machine-service.tenant-id:TENANT-001}") String tenantId
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.dispensingRepository = dispensingRepository;
        this.machineServiceClient = machineServiceClient;
        this.transactionReviewRepository = transactionReviewRepository;
        this.tenantId = tenantId;
    }

    public DispensingResponse requestDispensing(Long orderId) {
        Order order = findOrder(orderId);
        synchronized (order) {
            Payment payment = paymentRepository.findByOrderId(orderId)
                    .filter(value -> value.getStatus() == PaymentStatus.PAID)
                    .orElseThrow(() -> new PaymentNotAvailableException(orderId));
            DispensingCommand existing = dispensingRepository.findByOrderId(orderId).orElse(null);
            if (existing != null) {
                return toResponse(existing);
            }
            if (order.getStatus() != OrderStatus.CONFIRMED) {
                throw new PaymentNotAvailableException(orderId);
            }

            DispensingCommand command = dispensingRepository.saveIfAbsent(new DispensingCommand(
                    UUID.randomUUID().toString(),
                    orderId,
                    payment.getReference(),
                    order.getMachineId(),
                    order.getSlotId()
            ));
            if (!command.getStatus().equals(DispensingStatus.REQUESTED)) {
                return toResponse(command);
            }

            submit(order, command);
            return toResponse(command);
        }
    }

    public DispensingResponse getDispensingResult(Long orderId) {
        Order order = findOrder(orderId);
        DispensingCommand command = dispensingRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotAvailableException(orderId));
        DispensingStatus currentStatus = command.getStatus();
        if (currentStatus == DispensingStatus.DISPENSED || currentStatus == DispensingStatus.FAILED) {
            return toResponse(command);
        }

        try {
            MachineCommandResponse response = machineServiceClient.getResult(command.getCommandId());
            applyResponse(order, command, response);
        } catch (RestClientException exception) {
            markUnknown(command, "Machine Service result lookup failed: " + exception.getMessage());
        }
        return toResponse(command);
    }

    private void submit(Order order, DispensingCommand command) {
        try {
            MachineCommandResponse response = machineServiceClient.submit(new MachineCommandRequest(
                    tenantId,
                    command.getOrderId(),
                    command.getPaymentReference(),
                    command.getMachineId(),
                    command.getSlotId(),
                    command.getCommandId()
            ));
            applyResponse(order, command, response);
        } catch (RestClientException exception) {
            markUnknown(command, "Machine Service request outcome is uncertain: " + exception.getMessage());
        }
    }

    private void applyResponse(
            Order order,
            DispensingCommand command,
            MachineCommandResponse response
    ) {
        if (response == null
                || !command.getOrderId().equals(response.orderId())
                || !command.getCommandId().equals(response.commandId())) {
            markUnknown(command, "Machine Service returned an empty or mismatched command response");
            return;
        }

        DispensingStatus result = parseStatus(response.status());
        if (result == null || result == DispensingStatus.NOT_REQUESTED || result == DispensingStatus.REQUESTED) {
            markUnknown(command, "Machine Service returned an unsupported dispensing status");
            return;
        }
        String reviewReason = switch (result) {
            case UNKNOWN -> firstNonBlank(
                    response.message(),
                    "Machine Service cannot determine whether dispensing completed"
            );
            case FAILED -> firstNonBlank(response.message(), "Dispensing failed after payment was confirmed");
            default -> null;
        };
        command.recordResult(result, reviewReason);
        if (command.isManualReviewRequired()) {
            transactionReviewRepository.openOrUpdate(
                    command.getPaymentReference(),
                    command.getOrderId(),
                    command.getManualReviewReason()
            );
        }
        DispensingStatus effectiveStatus = command.getStatus();
        if (effectiveStatus == DispensingStatus.DISPENSED || effectiveStatus == DispensingStatus.FAILED) {
            synchronized (order) {
                if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PROCESSING) {
                    order.setStatus(effectiveStatus == DispensingStatus.DISPENSED
                            ? OrderStatus.DISPENSED
                            : OrderStatus.FAILED);
                    orderRepository.save(order);
                }
            }
        }
    }

    private DispensingStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return DispensingStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void markUnknown(DispensingCommand command, String reason) {
        command.recordResult(DispensingStatus.UNKNOWN, reason);
        transactionReviewRepository.openOrUpdate(
                command.getPaymentReference(),
                command.getOrderId(),
                command.getManualReviewReason()
        );
        LOGGER.warn("Dispensing command {} requires manual review: {}", command.getCommandId(), reason);
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private DispensingResponse toResponse(DispensingCommand command) {
        return new DispensingResponse(
                command.getOrderId(),
                command.getCommandId(),
                command.getMachineId(),
                command.getSlotId(),
                command.getStatus(),
                command.isManualReviewRequired(),
                command.getManualReviewReason(),
                command.getCreatedAt(),
                command.getResultReceivedAt()
        );
    }
}
