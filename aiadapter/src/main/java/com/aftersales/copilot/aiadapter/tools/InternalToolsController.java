package com.aftersales.copilot.aiadapter.tools;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/internal/v1/tools") public class InternalToolsController {
 private final JdbcTemplate jdbc; public InternalToolsController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @GetMapping("/orders/{orderNo}") public Map<String,Object> order(@PathVariable String orderNo){return jdbc.query("SELECT co.order_no orderNo,co.status,oi.product_name_snapshot productName,oi.paid_amount_cent paidAmountCent FROM customer_order co JOIN order_item oi ON oi.order_id=co.id WHERE co.order_no=? LIMIT 1",rs->{if(!rs.next())return Map.of("error","NOT_FOUND");return Map.of("orderNo",rs.getString("orderNo"),"status",rs.getString("status"),"productName",rs.getString("productName"),"paidAmountCent",rs.getLong("paidAmountCent"));},orderNo);}
 @GetMapping("/orders/{orderNo}/logistics") public List<Map<String,Object>> logistics(@PathVariable String orderNo){return jdbc.queryForList("SELECT le.event_type eventType,le.event_time eventTime,le.description FROM logistics_event le JOIN logistics_shipment ls ON ls.id=le.shipment_id JOIN customer_order co ON co.id=ls.order_id WHERE co.order_no=? ORDER BY le.event_time ASC",orderNo);}
 @GetMapping("/tickets/{id}/history") public List<Map<String,Object>> history(@PathVariable long id){return jdbc.queryForList("SELECT event_type eventType,summary,created_at createdAt FROM ticket_timeline WHERE ticket_id=? ORDER BY created_at ASC LIMIT 100",id);}
}
