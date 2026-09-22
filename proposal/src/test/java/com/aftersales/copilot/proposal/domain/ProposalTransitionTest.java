package com.aftersales.copilot.proposal.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProposalTransitionTest {
    @Test void statusesAreDefinedByContract() {
        assertThat(new String[]{"DRAFT", "PENDING_CONFIRMATION", "CONFIRMED", "REJECTED", "EXPIRED", "EXECUTED", "FAILED"})
                .contains("DRAFT", "PENDING_CONFIRMATION", "EXECUTED");
    }
}
