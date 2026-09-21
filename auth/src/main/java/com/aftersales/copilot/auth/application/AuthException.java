package com.aftersales.copilot.auth.application;

import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public AuthException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public static AuthException invalidCredentials() {
        return new AuthException("AUTH_INVALID_CREDENTIALS", "用户名或密码错误", HttpStatus.UNAUTHORIZED);
    }

    public static AuthException invalidRefreshToken() {
        return new AuthException("AUTH_INVALID_REFRESH_TOKEN", "Refresh Token 无效或已过期", HttpStatus.UNAUTHORIZED);
    }
}
