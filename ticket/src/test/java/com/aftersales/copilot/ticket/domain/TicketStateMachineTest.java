package com.aftersales.copilot.ticket.domain;

import com.aftersales.copilot.auth.domain.UserRole;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TicketStateMachineTest {
    private final TicketStateMachine machine = new TicketStateMachine();
    @Test void customerCanCancelPendingTicket() { assertThatCode(() -> machine.validate(UserRole.CUSTOMER, TicketStatus.PENDING_AGENT, TicketStatus.CANCELLED, "CANCEL")).doesNotThrowAnyException(); }
    @Test void agentCannotCloseActiveTicket() { assertThatThrownBy(() -> machine.validate(UserRole.AGENT, TicketStatus.PENDING_AGENT, TicketStatus.CLOSED, "CLOSE")).isInstanceOf(TicketException.class); }
    @Test void closedTicketCannotBeCancelled() { assertThatThrownBy(() -> machine.validate(UserRole.ADMIN, TicketStatus.CLOSED, TicketStatus.CANCELLED, "CANCEL")).isInstanceOf(TicketException.class); }
}
