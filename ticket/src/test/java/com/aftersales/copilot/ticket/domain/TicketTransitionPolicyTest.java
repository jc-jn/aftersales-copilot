package com.aftersales.copilot.ticket.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TicketTransitionPolicyTest {
    @Test
    void terminalStatesAreNotActive() {
        Set<TicketStatus> terminal = Set.of(TicketStatus.RESOLVED, TicketStatus.REJECTED, TicketStatus.CANCELLED, TicketStatus.CLOSED);
        assertThat(terminal).doesNotContain(TicketStatus.PENDING_AGENT, TicketStatus.PROCESSING);
    }
}
