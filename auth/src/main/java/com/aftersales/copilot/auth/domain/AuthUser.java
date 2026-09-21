package com.aftersales.copilot.auth.domain;

public record AuthUser(
        long id,
        String username,
        String email,
        String passwordHash,
        String displayName,
        UserRole role,
        UserStatus status
) {
}
