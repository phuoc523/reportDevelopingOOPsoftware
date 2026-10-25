package com.scentstation.identity.web;

import com.scentstation.identity.auth.AuthDto;
import com.scentstation.identity.rbac.RbacException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(RbacException.class)
    public ResponseEntity<AuthDto.ErrorResponse> rbacFailure(RbacException ex) {
        return ResponseEntity.status(ex.status()).cacheControl(CacheControl.noStore())
                .body(new AuthDto.ErrorResponse(ex.code(), ex.code()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<AuthDto.ErrorResponse> invalidRoleOrPermission(Exception ignored) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).cacheControl(CacheControl.noStore())
                .body(new AuthDto.ErrorResponse("INVALID_ROLE_OR_PERMISSION", "Unknown role or permission"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<AuthDto.ErrorResponse> invalidInput(Exception ignored) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).cacheControl(CacheControl.noStore())
                .body(new AuthDto.ErrorResponse("INVALID_REQUEST", "Invalid request body"));
    }
}
