package com.aftersales.copilot.aiadapter.security;

import com.aftersales.copilot.common.security.HmacSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InternalHmacService {
    private final String secret; private final Clock clock = Clock.systemUTC();
    private final Map<String,Long> nonces = new ConcurrentHashMap<>();
    public InternalHmacService(@Value("${app.ai.internal-secret}") String secret) { this.secret = secret; }
    public String signature(long timestamp, String nonce, String method, String path, String body) { return HmacSignature.sign(secret,timestamp,nonce,method,path,body); }
    public boolean verify(String service,long timestamp,String nonce,String method,String path,String body,String signature) {
        if (!"ai-service".equals(service) && !"aftersales-server".equals(service)) return false;
        if (Math.abs(clock.millis()-timestamp) > 300_000 || nonce == null || nonce.isBlank()) return false;
        Long prev = nonces.putIfAbsent(nonce, clock.millis());
        nonces.entrySet().removeIf(e -> clock.millis()-e.getValue() > 300_000);
        return prev == null && HmacSignature.constantTimeEquals(signature(timestamp,nonce,method,path,body), signature);
    }
}
