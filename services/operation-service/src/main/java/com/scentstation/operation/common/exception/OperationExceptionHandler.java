package com.scentstation.operation.common.exception;

import com.scentstation.operation.inventory.batch.exception.InvalidInventoryBatchDateException;
import com.scentstation.operation.inventory.batch.exception.InventoryBatchAlreadyExistsException;
import com.scentstation.operation.inventory.batch.exception.InventoryBatchExpiredException;
import com.scentstation.operation.inventory.batch.exception.InventoryBatchNotFoundException;
import com.scentstation.operation.inventory.bottle.exception.BottleAlreadyExistsException;
import com.scentstation.operation.inventory.bottle.exception.BottleBatchMismatchException;
import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import com.scentstation.operation.inventory.consumption.exception.BottleInstallationMismatchException;
import com.scentstation.operation.inventory.consumption.exception.DispenseConsumptionNotFoundException;
import com.scentstation.operation.inventory.consumption.exception.DuplicateDispenseCommandException;
import com.scentstation.operation.inventory.consumption.exception.InsufficientBottleQuantityException;
import com.scentstation.operation.inventory.estimation.exception.SprayCalibrationNotFoundException;
import com.scentstation.operation.inventory.installation.exception.ActiveInstallationNotFoundException;
import com.scentstation.operation.inventory.installation.exception.BottleAlreadyInstalledException;
import com.scentstation.operation.inventory.installation.exception.BottleNotInstallableException;
import com.scentstation.operation.inventory.installation.exception.SlotAlreadyOccupiedException;
import com.scentstation.operation.inventory.integration.machine.exception.MachineSlotProductMismatchException;
import com.scentstation.operation.inventory.integration.machine.exception.MachineSlotUnavailableException;
import com.scentstation.operation.inventory.refill.exception.BottleNotRefillableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.scentstation.operation.inventory.reconciliation.exception.SensorInstallationMismatchException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class OperationExceptionHandler {

    // Trả 404 khi không tìm thấy inventory batch.
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

    // Trả 404 khi không tìm thấy bottle trong tenant.
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

    // Trả 409 khi mã batch đã tồn tại.
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

    // Trả 409 khi mã bottle đã tồn tại.
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

    // Trả 400 khi khoảng ngày của batch không hợp lệ.
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

    // Trả 400 khi thao tác dùng batch đã hết hạn.
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

    // Trả 400 khi product của bottle không khớp batch.
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

    // Trả 404 khi không tìm thấy dispense command.
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

    // Tổng hợp lỗi validation request body thành response 400.
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

    // Trả 400 khi bottle chưa có calibration hợp lệ.
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

    // Tổng hợp lỗi validation path và query parameter thành response 400.
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

    // Trả 409 khi dispense command đã được xử lý.
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

    // Trả 409 khi bottle không nằm đúng machine slot yêu cầu.
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

    // Trả 409 khi bottle không đủ lượng để dispense.
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

    // Tạo response lỗi thống nhất cho toàn operation-service.
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

    // Trả 404 khi bottle không có installation active.
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

    // Trả 409 khi bottle đã được lắp đặt.
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

    // Trả 409 khi machine slot đã bị chiếm.
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

    // Trả 400 khi trạng thái bottle không cho phép lắp.
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

    // Trả 400 khi trạng thái bottle không cho phép refill.
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

    // Trả 409 khi machine slot inactive hoặc maintenance.
    @ExceptionHandler(MachineSlotUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleMachineSlotUnavailable(
            MachineSlotUnavailableException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request
        );
    }

    // Trả 409 khi product của slot không tương thích bottle.
    @ExceptionHandler(MachineSlotProductMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleMachineSlotProductMismatch(
            MachineSlotProductMismatchException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request
        );
    }

        // Trả 409 khi sensor reading không khớp installation.
        @ExceptionHandler(SensorInstallationMismatchException.class)
        public ResponseEntity<ApiErrorResponse> handleSensorInstallationMismatch(
                SensorInstallationMismatchException exception,
                HttpServletRequest request
        ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request
        );
        }
}
