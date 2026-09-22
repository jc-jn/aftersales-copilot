package com.aftersales.copilot.proposal.domain;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.assertThat;

class ProposalIdempotencyTest {
    @Test void sameRequestProducesStableHash() throws Exception {
        String first = hash("proposal:7:ticket:3");
        String second = hash("proposal:7:ticket:3");
        assertThat(first).isEqualTo(second).hasSize(64);
    }
    @Test void differentRequestProducesDifferentHash() throws Exception {
        assertThat(hash("proposal:7:ticket:3")).isNotEqualTo(hash("proposal:7:ticket:4"));
    }
    private String hash(String value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
}
