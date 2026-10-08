package com.aftersales.copilot.auth.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SecurityStateStore {
    private static final DefaultRedisScript<Long> COUNTER = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('PEXPIRE',KEYS[1],ARGV[1]) end; return n", Long.class);
    private final StringRedisTemplate redis;
    private final boolean localFallback;
    private final Map<String, Entry> local = new HashMap<>();

    public SecurityStateStore(StringRedisTemplate redis, @Value("${app.security.local-state-fallback:false}") boolean localFallback) {
        this.redis = redis;
        this.localFallback = localFallback;
    }

    public long increment(String key, Duration ttl) {
        try {
            Long value = redis.execute(COUNTER, List.of("aftersales:rate:" + key), Long.toString(ttl.toMillis()));
            if (value == null) throw new IllegalStateException("No Redis result");
            return value;
        } catch (RuntimeException e) {
            if (!localFallback) throw unavailable(e);
            return localValue("rate:" + key, ttl, false);
        }
    }

    public boolean claimNonce(String key) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("aftersales:nonce:" + key, "1", Duration.ofMinutes(5)));
        } catch (RuntimeException e) {
            if (!localFallback) throw unavailable(e);
            return localValue("nonce:" + key, Duration.ofMinutes(5), true) == 1;
        }
    }

    private synchronized long localValue(String key, Duration ttl, boolean nonce) {
        long now = System.currentTimeMillis();
        local.entrySet().removeIf(e -> e.getValue().expiresAt <= now);
        Entry entry = local.get(key);
        if (entry == null) {
            if (local.size() >= 10000) throw unavailable(null);
            entry = new Entry(now + ttl.toMillis());
            local.put(key, entry);
        }
        if (nonce && entry.count > 0) return 2;
        return ++entry.count;
    }

    private ResponseStatusException unavailable(Throwable cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SECURITY_STATE_UNAVAILABLE", cause);
    }

    private static final class Entry {
        final long expiresAt;
        long count;
        Entry(long expiresAt) { this.expiresAt = expiresAt; }
    }
}
