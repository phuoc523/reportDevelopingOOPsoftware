package com.scentstation.operation.inventory.batch.exception;

import com.scentstation.operation.inventory.bottle.exception.BottleAlreadyExistsException;
import com.scentstation.operation.inventory.bottle.exception.BottleBatchMismatchException;
import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.scentstation.operation.inventory.installation.exception.ActiveInstallationNotFoundException;
import com.scentstation.operation.inventory.installation.exception.BottleAlreadyInstalledException;
import com.scentstation.operation.inventory.installation.exception.BottleNotInstallableException;
import com.scentstation.operation.inventory.installation.exception.SlotAlreadyOccupiedException;
import com.scentstation.operation.inventory.refill.exception.BottleNotRefillableException;
import com.scentstation.operation.inventory.consumption.exception.BottleInstallationMismatchException;
import com.scentstation.operation.inventory.consumption.exception.DuplicateDispenseCommandException;
import com.scentstation.operation.inventory.consumption.exception.InsufficientBottleQuantityException;
import com.scentstation.operation.inventory.consumption.exception.DispenseConsumptionNotFoundException;
import com.scentstation.operation.inventory.estimation.exception.SprayCalibrationNotFoundException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class OperationExceptionHandler {

    @ExceptionHandler(InventoryBatchNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleBatchNotFound(
            InventoryBatchNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(BottleNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleBottleNotFound(
            BottleNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(InventoryBatchAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleBatchAlreadyExists(
            InventoryBatchAlreadyExistsException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(BottleAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleBottleAlreadyExists(
            BottleAlreadyExistsException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(InvalidInventoryBatchDateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBatchDate(
            InvalidInventoryBatchDateException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(InventoryBatchExpiredException.class)
    public ResponseEntity<ApiErrorResponse> handleExpiredBatch(
            InventoryBatchExpiredException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(BottleBatchMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleBottleBatchMismatch(
            BottleBatchMismatchException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(DispenseConsumptionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleDispenseConsumptionNotFound(
            DispenseConsumptionNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleRequestValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error ->
                        error.getField()
                                + ": "
                                + error.getDefaultMessage()
                )
                .collect(Collectors.joining(", "));

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                message,
                request
        );
    }

        @ExceptionHandler(SprayCalibrationNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleSprayCalibrationNotFound(
                SprayCalibrationNotFoundException exception,
                HttpServletRequest request
        ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request
        );
        }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        String message = exception.getConstraintViolations()
                .stream()
                .map(violation ->
                        violation.getPropertyPath()
                                + ": "
                                + violation.getMessage()
                )
                .collect(Collectors.joining(", "));

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                message,
                request
        );
    }

    @ExceptionHandler(DuplicateDispenseCommandException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateDispenseCommand(
            DuplicateDispenseCommandException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(BottleInstallationMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleBottleInstallationMismatch(
            BottleInstallationMismatchException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(InsufficientBottleQuantityException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientBottleQuantity(
            InsufficientBottleQuantityException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request
        );
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(ActiveInstallationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleActiveInstallationNotFound(
            ActiveInstallationNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(BottleAlreadyInstalledException.class)
    public ResponseEntity<ApiErrorResponse> handleBottleAlreadyInstalled(
            BottleAlreadyInstalledException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(SlotAlreadyOccupiedException.class)
    public ResponseEntity<ApiErrorResponse> handleSlotAlreadyOccupied(
            SlotAlreadyOccupiedException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(BottleNotInstallableException.class)
    public ResponseEntity<ApiErrorResponse> handleBottleNotInstallable(
            BottleNotInstallableException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(BottleNotRefillableException.class)
    public ResponseEntity<ApiErrorResponse> handleBottleNotRefillable(
            BottleNotRefillableException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request
        );
    }
}