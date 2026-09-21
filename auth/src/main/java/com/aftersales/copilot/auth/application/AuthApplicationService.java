package com.aftersales.copilot.auth.application;

import com.aftersales.copilot.auth.api.UserResponse;
import com.aftersales.copilot.auth.domain.AuthUser;
import com.aftersales.copilot.auth.domain.UserStatus;
import com.aftersales.copilot.auth.infrastructure.RefreshTokenRepository;
import com.aftersales.copilot.auth.infrastructure.SnowflakeIdGenerator;
import com.aftersales.copilot.auth.infrastructure.UserRepository;
import io.jsonwebtoken.JwtException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

@Service
public class AuthApplicationService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final SnowflakeIdGenerator idGenerator;

    public AuthApplicationService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                                  JwtService jwtService, PasswordEncoder passwordEncoder, SnowflakeIdGenerator idGenerator) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public TokenPair login(String login, String password, String deviceInfo) {
        AuthUser user = userRepository.findByLogin(login.trim()).orElseThrow(AuthException::invalidCredentials);
        if (user.status() != UserStatus.ACTIVE || !passwordEncoder.matches(password, user.passwordHash())) {
            throw AuthException.invalidCredentials();
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        userRepository.updateLastLogin(user.id(), now);
        return issuePair(user, deviceInfo, now);
    }

    @Transactional
    public TokenPair refresh(String refreshToken, String deviceInfo) {
        long userId;
        try {
            userId = jwtService.parseRefreshTokenUserId(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw AuthException.invalidRefreshToken();
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        RefreshTokenRepository.StoredRefreshToken stored = refreshTokenRepository.findByHashForUpdate(hash(refreshToken))
                .orElseThrow(AuthException::invalidRefreshToken);
        if (stored.userId() != userId || !stored.isUsableAt(now)) {
            throw AuthException.invalidRefreshToken();
        }
        AuthUser user = userRepository.findActiveById(userId).orElseThrow(AuthException::invalidRefreshToken);
        refreshTokenRepository.revoke(stored.id(), now);
        return issuePair(user, deviceInfo, now);
    }

    @Transactional
    public void logout(String refreshToken) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        refreshTokenRepository.findByHashForUpdate(hash(refreshToken))
                .filter(token -> token.isUsableAt(now))
                .ifPresent(token -> refreshTokenRepository.revoke(token.id(), now));
    }

    private TokenPair issuePair(AuthUser user, String deviceInfo, LocalDateTime now) {
        String accessToken = jwtService.createAccessToken(user);
        String refreshToken = jwtService.createRefreshToken(user);
        long id = idGenerator.nextId();
        try {
            refreshTokenRepository.save(id, user.id(), hash(refreshToken),
                    LocalDateTime.ofInstant(jwtService.refreshExpiresAt(), ZoneOffset.UTC), normalizeDeviceInfo(deviceInfo), now);
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("Unable to persist refresh token", ex);
        }
        return new TokenPair(accessToken, refreshToken, jwtService.accessExpiresInSeconds(), UserResponse.from(user));
    }

    private static String normalizeDeviceInfo(String deviceInfo) {
        if (deviceInfo == null || deviceInfo.isBlank()) {
            return null;
        }
        return deviceInfo.substring(0, Math.min(deviceInfo.length(), 255));
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
