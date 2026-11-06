package com.scentstation.identity.auth;

public record LoginOutcome(AuthDto.LoginResponse success, String errorCode, int status) {
    public static LoginOutcome ok(AuthDto.LoginResponse body) { return new LoginOutcome(body, null, 200); }
    public static LoginOutcome fail(String code, int status) { return new LoginOutcome(null, code, status); }
    public boolean succeeded() { return success != null; }
}
