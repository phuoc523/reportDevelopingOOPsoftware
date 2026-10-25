package com.scentstation.identity.rbac;

import org.springframework.http.HttpStatus;

public class RbacException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public RbacException(HttpStatus status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
