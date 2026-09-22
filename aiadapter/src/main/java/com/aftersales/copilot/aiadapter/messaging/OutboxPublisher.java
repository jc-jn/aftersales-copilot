package com.aftersales.copilot.aiadapter.messaging;
import org.springframework.amqp.rabbit.core.RabbitTemplate; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Component; import java.time.*; import java.util.*;
@Component public class OutboxPublisher {
 private final JdbcTemplate jdbc; private final RabbitTemplate rabbit;
 public OutboxPublisher(JdbcTemplate jdbc,RabbitTemplate rabbit){this.jdbc=jdbc;this.rabbit=rabbit;}
 @Scheduled(fixedDelayString="${app.ai.outbox-interval-ms:1000}") public void publish(){
  List<Map<String,Object>> rows=jdbc.queryForList("SELECT event_id,routing_key,payload FROM outbox_event WHERE status IN ('NEW','FAILED') AND retry_count < 3 AND (next_retry_at IS NULL OR next_retry_at<=UTC_TIMESTAMP(3)) ORDER BY created_at LIMIT 50");
  for(var row:rows){String id=String.valueOf(row.get("event_id")); try{jdbc.update("UPDATE outbox_event SET status='SENDING',updated_at=? WHERE event_id=? AND status IN ('NEW','FAILED')",LocalDateTime.now(ZoneOffset.UTC),id); rabbit.convertAndSend("aftersales.topic",String.valueOf(row.get("routing_key")),row.get("payload")); jdbc.update("UPDATE outbox_event SET status='PUBLISHED',published_at=?,updated_at=? WHERE event_id=?",LocalDateTime.now(ZoneOffset.UTC),LocalDateTime.now(ZoneOffset.UTC),id);}catch(Exception e){jdbc.update("UPDATE outbox_event SET status='FAILED',retry_count=retry_count+1,next_retry_at=?,updated_at=? WHERE event_id=?",LocalDateTime.now(ZoneOffset.UTC).plusSeconds(30),LocalDateTime.now(ZoneOffset.UTC),id);}}
 }
}
