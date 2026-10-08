package com.aftersales.copilot.aiadapter.messaging;

import com.aftersales.copilot.common.observability.TraceIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;

    public OutboxPublisher(JdbcTemplate jdbc, RabbitTemplate rabbit, ObjectMapper mapper) {
        this.jdbc = jdbc; this.rabbit = rabbit; this.mapper = mapper;
    }

    @Scheduled(fixedDelayString="${app.ai.outbox-interval-ms:1000}")
    public void publish() {
        java.util.List<Map<String,Object>> rows;
        try {
            rows = jdbc.queryForList("SELECT event_id,routing_key,payload FROM outbox_event WHERE status IN ('NEW','FAILED') AND retry_count<3 AND (next_retry_at IS NULL OR next_retry_at<=UTC_TIMESTAMP(3)) ORDER BY created_at LIMIT 50");
        } catch (RuntimeException e) {
            log.atWarn().addKeyValue("event", "outbox_poll_failed").addKeyValue("errorType", e.getClass().getSimpleName()).log("Outbox polling unavailable");
            return;
        }
        for (var row : rows) {
            String id = String.valueOf(row.get("event_id")), previous = MDC.get("traceId");
            try {
                var envelope = mapper.readValue(String.valueOf(row.get("payload")), Map.class);
                MDC.put("traceId", TraceIds.normalize((String) envelope.get("traceId")));
                int claimed = jdbc.update("UPDATE outbox_event SET status='SENDING',updated_at=? WHERE event_id=? AND status IN ('NEW','FAILED')", now(), id);
                if (claimed != 1) continue;
                rabbit.convertAndSend("aftersales.topic", String.valueOf(row.get("routing_key")), String.valueOf(row.get("payload")));
                jdbc.update("UPDATE outbox_event SET status='PUBLISHED',published_at=?,updated_at=? WHERE event_id=?", now(), now(), id);
                log.atInfo().addKeyValue("event", "outbox_published").addKeyValue("eventId", id).log("Outbox event published");
            } catch (Exception e) {
                jdbc.update("UPDATE outbox_event SET status='FAILED',retry_count=retry_count+1,next_retry_at=?,updated_at=? WHERE event_id=?", now().plusSeconds(30), now(), id);
                log.atWarn().addKeyValue("event", "outbox_publish_failed").addKeyValue("eventId", id)
                        .addKeyValue("errorType", e.getClass().getSimpleName()).log("Outbox publication failed");
            } finally {
                if (previous == null) MDC.remove("traceId"); else MDC.put("traceId", previous);
            }
        }
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}
