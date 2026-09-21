package com.aftersales.copilot.auth.api;

import com.aftersales.copilot.auth.domain.AuthUser;
import com.aftersales.copilot.auth.domain.AuthenticatedUser;

public record UserResponse(String id, String username, String email, String displayName, String role) {
    public static UserResponse from(AuthUser user) {
        return new UserResponse(Long.toUnsignedString(user.id()), user.username(), user.email(), user.displayName(), user.role().name());
    }

    public static UserResponse from(AuthenticatedUser user) {
        return new UserResponse(Long.toUnsignedString(user.id()), user.username(), user.email(), user.displayName(), user.role().name());
    }
}
