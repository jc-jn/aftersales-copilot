package com.aftersales.copilot.auth.application;

import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import com.aftersales.copilot.auth.domain.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class TicketAccessGuard {
    private final JdbcTemplate jdbc;

    public TicketAccessGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void requireAccess(AuthenticatedUser user, long ticketId) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED");
        var ticket = jdbc.queryForList("SELECT customer_id,assigned_agent_id FROM service_ticket WHERE id=?", ticketId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND"));
        Number customer = (Number) ticket.get("customer_id"), agent = (Number) ticket.get("assigned_agent_id");
        if ((user.role() == UserRole.CUSTOMER && customer.longValue() != user.id())
                || (user.role() == UserRole.AGENT && (agent == null || agent.longValue() != user.id()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TICKET_ACCESS_DENIED");
        }
    }
}
