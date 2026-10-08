package com.aftersales.copilot.aiadapter.tools;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/tools/tickets/{ticketId}")
public class InternalToolsController {
    private final JdbcTemplate jdbc;
    public InternalToolsController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/orders/{orderNo}")
    public Map<String, Object> order(@PathVariable long ticketId, @PathVariable String orderNo) {
        requireOrder(ticketId, orderNo);
        return jdbc.queryForList("SELECT co.order_no orderNo,co.status,oi.product_name_snapshot productName,oi.paid_amount_cent paidAmountCent FROM customer_order co JOIN service_ticket t ON t.order_id=co.id JOIN order_item oi ON oi.id=t.order_item_id WHERE t.id=? AND co.order_no=?", ticketId, orderNo).getFirst();
    }

    @GetMapping("/orders/{orderNo}/logistics")
    public List<Map<String, Object>> logistics(@PathVariable long ticketId, @PathVariable String orderNo) {
        requireOrder(ticketId, orderNo);
        return jdbc.queryForList("SELECT le.status_code eventType,le.event_time eventTime,le.description FROM logistics_event le JOIN logistics_shipment ls ON ls.id=le.shipment_id JOIN customer_order co ON co.id=ls.order_id WHERE co.order_no=? ORDER BY le.event_time ASC", orderNo);
    }

    @GetMapping("/history")
    public List<Map<String, Object>> history(@PathVariable long ticketId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM service_ticket WHERE id=?", Integer.class, ticketId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND");
        }
        return jdbc.queryForList("SELECT event_type eventType,summary,created_at createdAt FROM ticket_timeline WHERE ticket_id=? AND event_type<>'INTERNAL_NOTE' ORDER BY created_at ASC LIMIT 100", ticketId);
    }

    private void requireOrder(long ticketId, String orderNo) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM service_ticket t JOIN customer_order co ON co.id=t.order_id WHERE t.id=? AND co.order_no=?", Integer.class, ticketId, orderNo);
        if (count == null || count != 1) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TOOL_RESOURCE_NOT_BOUND");
    }
}
