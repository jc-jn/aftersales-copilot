package com.aftersales.copilot;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

@Component
public class ProductionSecurityValidator {
    public ProductionSecurityValidator(Environment env) {
        if (!Arrays.asList(env.getActiveProfiles()).contains("prod")) return;
        if (Arrays.stream(env.getActiveProfiles()).anyMatch(p -> p.equals("local") || p.equals("demo"))) {
            throw new IllegalStateException("prod cannot be combined with local/demo profiles");
        }
        String jwt = env.getRequiredProperty("app.security.jwt-secret");
        String ai = env.getRequiredProperty("app.ai.internal-secret");
        String java = env.getRequiredProperty("app.ai.java-internal-secret");
        for (String secret : new String[]{jwt, ai, java}) {
            if (secret.getBytes(StandardCharsets.UTF_8).length < 32 || isPlaceholder(secret)) {
                throw new IllegalStateException("Production signing secrets must be random and at least 32 bytes");
            }
        }
        if (jwt.equals(ai) || jwt.equals(java) || ai.equals(java)) {
            throw new IllegalStateException("JWT and service identities require distinct secrets");
        }
        if (!"false".equals(env.getProperty("app.security.local-state-fallback", "false"))) {
            throw new IllegalStateException("Production cannot fall back to process-local security state");
        }
        for (String key : new String[]{"spring.datasource.password", "spring.rabbitmq.password",
                "spring.data.redis.password", "app.storage.access-key", "app.storage.secret-key"}) {
            String value = env.getProperty(key, "");
            if (value.isBlank() || isPlaceholder(value) || value.equals("minioadmin")) {
                throw new IllegalStateException("Production credentials must be explicitly configured: " + key);
            }
        }
    }

    private boolean isPlaceholder(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("local-development") || normalized.contains("change-me")
                || normalized.contains("replace-with") || normalized.contains("dev_password")
                || normalized.contains("<strong-random>");
    }
}
