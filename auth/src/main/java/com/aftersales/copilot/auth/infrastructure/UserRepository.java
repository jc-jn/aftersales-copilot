package com.aftersales.copilot.auth.infrastructure;

import com.aftersales.copilot.auth.domain.AuthUser;
import com.aftersales.copilot.auth.domain.UserRole;
import com.aftersales.copilot.auth.domain.UserStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class UserRepository {
    private final AuthUserMapper mapper;

    public UserRepository(AuthUserMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<AuthUser> findByLogin(String login) {
        return mapper.findByLogin(login);
    }

    public Optional<AuthUser> findActiveById(long id) {
        return mapper.findActiveById(id);
    }

    public void updateLastLogin(long id, LocalDateTime now) {
        mapper.updateLastLogin(id, now);
    }
}
