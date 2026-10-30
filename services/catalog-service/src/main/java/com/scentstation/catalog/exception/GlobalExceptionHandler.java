package com.scentstation.catalog.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Chuyển lỗi không tìm thấy sản phẩm thành phản hồi HTTP 404 thống nhất.
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProductNotFound(
            ProductNotFoundException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    // Tổng hợp lỗi kiểm tra dữ liệu đầu vào và trả về phản hồi HTTP 400.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                request.getRequestURI(),
                LocalDateTime.now()
        );

        return ResponseEntity.badRequest().body(response);
    }

    // Định dạng lỗi của một trường thành thông báo dễ đọc.
    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField()
                + ": "
                + fieldError.getDefaultMessage();
    }

        // Trả về 404 khi campaign không tồn tại trong tenant yêu cầu.
        @ExceptionHandler(CampaignNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleCampaignNotFound(
                CampaignNotFoundException ex,
                HttpServletRequest request
        ) {
        ApiErrorResponse response = ApiErrorResponse.builder()
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
        }

                // Trả về 400 khi khoảng thời gian campaign không hợp lệ.
                @ExceptionHandler(InvalidCampaignPeriodException.class)
                public ResponseEntity<ApiErrorResponse> handleInvalidCampaignPeriod(
                        InvalidCampaignPeriodException ex,
                        HttpServletRequest request
                ) {
                ApiErrorResponse response = ApiErrorResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .timestamp(LocalDateTime.now())
                        .build();

                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(response);
                }

                // Trả về 400 khi giá trị giảm giá không hợp lệ.
                @ExceptionHandler(InvalidDiscountValueException.class)
                public ResponseEntity<ApiErrorResponse> handleInvalidDiscountValue(
                        InvalidDiscountValueException ex,
                        HttpServletRequest request
                ) {
                ApiErrorResponse response = ApiErrorResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .timestamp(LocalDateTime.now())
                        .build();

                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(response);
                }

        // Trả về 400 khi điều kiện tìm kiếm catalog không hợp lệ.
        @ExceptionHandler(InvalidProductSearchCriteriaException.class)
        public ResponseEntity<ApiErrorResponse> handleInvalidProductSearchCriteria(
                InvalidProductSearchCriteriaException ex,
                HttpServletRequest request
        ) {
        return ResponseEntity
                .badRequest()
                .body(
                        buildError(
                                HttpStatus.BAD_REQUEST,
                                ex.getMessage(),
                                request
                        )
                );
        }

        // Tạo response lỗi API thống nhất cho các exception nghiệp vụ.
        private ApiErrorResponse buildError(
                HttpStatus status,
                String message,
                HttpServletRequest request
        ) {
        return ApiErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        }

        // Trả về 409 khi Machine Service trả thông tin không khớp resource Catalog yêu cầu.
        @ExceptionHandler(MachineAvailabilityReferenceMismatchException.class)
        public ResponseEntity<ApiErrorResponse> handleMachineAvailabilityReferenceMismatch(
                MachineAvailabilityReferenceMismatchException ex,
                HttpServletRequest request
        ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        buildError(
                                HttpStatus.CONFLICT,
                                ex.getMessage(),
                                request
                        )
                );
        }

        // Trả về 400 khi tham số path/query không thỏa constraint validation.
        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
                ConstraintViolationException ex,
                HttpServletRequest request
        ) {
        return ResponseEntity
                .badRequest()
                .body(
                        buildError(
                                HttpStatus.BAD_REQUEST,
                                ex.getMessage(),
                                request
                        )
                );
        }
}
