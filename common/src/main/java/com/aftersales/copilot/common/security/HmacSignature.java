package com.aftersales.copilot.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class HmacSignature {
    private HmacSignature() {}
    public static String sha256Hex(String value) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static String sign(String secret, long timestamp, String nonce, String method, String path, String body) {
        String canonical = timestamp + "\n" + nonce + "\n" + method.toUpperCase() + "\n" + path + "\n" + sha256Hex(body == null ? "" : body);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static boolean constantTimeEquals(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
    private static String hex(byte[] bytes) { var sb = new StringBuilder(bytes.length * 2); for (byte b: bytes) sb.append(String.format("%02x", b)); return sb.toString(); }
}
