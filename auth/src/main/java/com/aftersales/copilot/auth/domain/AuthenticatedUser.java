package com.aftersales.copilot.auth.domain;

import java.security.Principal;

public record AuthenticatedUser(long id, String username, String email, String displayName, UserRole role) implements Principal {
    @Override
    public String getName() {
        return username;
    }
}
