package com.aftersales.copilot.aiadapter.security;

import com.aftersales.copilot.common.security.HmacSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Clock;
import com.aftersales.copilot.auth.infrastructure.SecurityStateStore;

@Component
public class InternalHmacService {
    private final String secret, outboundSecret; private final Clock clock = Clock.systemUTC();
    private final SecurityStateStore state;
    public InternalHmacService(@Value("${app.ai.internal-secret}") String secret,
                              @Value("${app.ai.java-internal-secret}") String outboundSecret, SecurityStateStore state) {
        this.secret = secret; this.outboundSecret = outboundSecret; this.state = state;
    }
    public String signature(long timestamp, String nonce, String method, String path, String body) { return HmacSignature.sign(outboundSecret,timestamp,nonce,method,path,body); }
    public boolean verify(String service,long timestamp,String nonce,String method,String path,String body,String signature) {
        if (!"ai-service".equals(service)) return false;
        long now = clock.millis();
        if (timestamp < now - 300_000 || timestamp > now + 300_000 || nonce == null
                || !nonce.matches("[A-Za-z0-9_-]{1,128}") || signature == null || !signature.matches("[a-f0-9]{64}")) return false;
        if (!HmacSignature.constantTimeEquals(HmacSignature.sign(secret,timestamp,nonce,method,path,body), signature)) return false;
        return state.claimNonce(service + ":" + nonce);
    }
}
