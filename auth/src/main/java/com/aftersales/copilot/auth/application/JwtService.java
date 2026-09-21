package com.aftersales.copilot.auth.application;

import com.aftersales.copilot.auth.domain.AuthUser;
import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Clock clock;

    public JwtService(
            @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.jwt-issuer:aftersales-server}") String issuer,
            @Value("${app.security.jwt-access-ttl:PT15M}") Duration accessTtl,
            @Value("${app.security.jwt-refresh-ttl:P7D}") Duration refreshTtl) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.issuer = issuer;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.clock = Clock.systemUTC();
    }

    public String createAccessToken(AuthUser user) {
        return createToken(user, "access", accessTtl);
    }

    public String createRefreshToken(AuthUser user) {
        return createToken(user, "refresh", refreshTtl);
    }

    private String createToken(AuthUser user, String type, Duration ttl) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(issuer)
                .subject(Long.toUnsignedString(user.id()))
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claim("type", type)
                .claim("username", user.username())
                .claim("email", user.email())
                .claim("displayName", user.displayName())
                .claim("role", user.role().name())
                .signWith(key)
                .compact();
    }

    public AuthenticatedUser parseAccessToken(String token) {
        Claims claims = parse(token, "access");
        return new AuthenticatedUser(
                Long.parseUnsignedLong(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("email", String.class),
                claims.get("displayName", String.class),
                com.aftersales.copilot.auth.domain.UserRole.valueOf(claims.get("role", String.class)));
    }

    public long parseRefreshTokenUserId(String token) {
        return Long.parseUnsignedLong(parse(token, "refresh").getSubject());
    }

    private Claims parse(String token, String expectedType) {
        Claims claims = Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                .parseSignedClaims(token).getPayload();
        if (!expectedType.equals(claims.get("type", String.class))) {
            throw new UnsupportedJwtException("Unexpected JWT token type");
        }
        return claims;
    }

    public long accessExpiresInSeconds() {
        return accessTtl.toSeconds();
    }

    public Instant refreshExpiresAt() {
        return clock.instant().plus(refreshTtl);
    }
}
