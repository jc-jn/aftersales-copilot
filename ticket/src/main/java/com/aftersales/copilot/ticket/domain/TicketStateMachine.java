package com.aftersales.copilot.ticket.domain;

import com.aftersales.copilot.auth.domain.UserRole;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class TicketStateMachine {
    private static final Set<TicketStatus> CANCELLABLE = Set.of(TicketStatus.SUBMITTED, TicketStatus.PENDING_ASSIGNMENT,
            TicketStatus.PENDING_AGENT, TicketStatus.PENDING_CUSTOMER, TicketStatus.WAITING_RETURN,
            TicketStatus.RETURN_IN_TRANSIT, TicketStatus.RETURN_RECEIVED);
    private static final Set<TicketStatus> REJECTABLE = Set.of(TicketStatus.SUBMITTED, TicketStatus.PENDING_ASSIGNMENT,
            TicketStatus.PENDING_AGENT, TicketStatus.PENDING_CUSTOMER);

    public void validate(UserRole role, TicketStatus from, TicketStatus to, String action) {
        boolean allowed = switch (to) {
            case PENDING_CUSTOMER -> role != UserRole.CUSTOMER && from == TicketStatus.PENDING_AGENT;
            case PENDING_AGENT -> role == UserRole.CUSTOMER && from == TicketStatus.PENDING_CUSTOMER;
            case REJECTED -> role != UserRole.CUSTOMER && REJECTABLE.contains(from);
            case CANCELLED -> (role == UserRole.CUSTOMER || role == UserRole.ADMIN) && CANCELLABLE.contains(from);
            case CLOSED -> from == TicketStatus.RESOLVED || from == TicketStatus.REJECTED || from == TicketStatus.CANCELLED;
            default -> false;
        };
        if (!allowed) throw TicketException.invalid(from.name(), action);
    }
}
