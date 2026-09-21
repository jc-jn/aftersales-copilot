package com.aftersales.copilot.auth.infrastructure;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class RefreshTokenRepository {
    private final RefreshTokenMapper mapper;

    public RefreshTokenRepository(RefreshTokenMapper mapper) {
        this.mapper = mapper;
    }

    public void save(long id, long userId, String tokenHash, LocalDateTime expiresAt, String deviceInfo, LocalDateTime now) {
        mapper.save(id, userId, tokenHash, expiresAt, deviceInfo, now);
    }

    public Optional<StoredRefreshToken> findByHashForUpdate(String tokenHash) {
        return mapper.findByHashForUpdate(tokenHash);
    }

    public void revoke(long id, LocalDateTime now) {
        mapper.revoke(id, now);
    }

    public record StoredRefreshToken(long id, long userId, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        public boolean isUsableAt(LocalDateTime now) {
            return revokedAt == null && expiresAt.isAfter(now);
        }
    }
}
