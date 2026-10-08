package com.aftersales.copilot.aiadapter.application;

import com.aftersales.copilot.common.ai.AiTaskScheduler;
import com.aftersales.copilot.common.ai.AiTaskRetryService;
import com.aftersales.copilot.common.observability.TraceIds;
import com.aftersales.copilot.statistics.application.AiCallService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class AiTaskApplicationService implements AiTaskScheduler, AiTaskRetryService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AiCallService calls;

    public AiTaskApplicationService(JdbcTemplate jdbc, ObjectMapper json, AiCallService calls) {
        this.jdbc = jdbc; this.json = json; this.calls = calls;
    }

    @Override @Transactional
    public void scheduleTicketAnalysis(long ticketId, int ticketVersion, long customerId) {
        long taskId = id();
        LocalDateTime now = now();
        try {
            jdbc.update("INSERT INTO ai_task(id,biz_type,biz_id,dedup_key,status,attempt_count,max_attempts,request_snapshot,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    taskId, "TICKET_ANALYSIS", ticketId, "TICKET_ANALYSIS:" + ticketId + ":" + ticketVersion,
                    "PENDING", 0, 3, jsonValue(Map.of("ticketId", ticketId, "ticketVersion", ticketVersion, "customerId", customerId)), now, now);
        } catch (DuplicateKeyException e) { return; }
        enqueue(taskId, ticketId, ticketVersion, now);
    }

    private void enqueue(long taskId, long ticketId, int ticketVersion, LocalDateTime now) {
        String callId = UUID.randomUUID().toString(), traceId = TraceIds.normalize(MDC.get("traceId"));
        calls.begin(callId, taskId, ticketId, "TICKET_ANALYSIS", traceId);
        var data = Map.of("taskId", taskId, "ticketId", ticketId, "ticketVersion", ticketVersion, "callId", callId);
        var envelope = Map.of("eventId", UUID.randomUUID().toString(), "eventType", "ticket.ai.analyze.requested.v1",
                "occurredAt", now.toString(), "traceId", traceId, "producer", "aftersales-server", "schemaVersion", 1, "data", data);
        jdbc.update("INSERT INTO outbox_event(event_id,aggregate_type,aggregate_id,event_type,routing_key,payload,status,retry_count,created_at,updated_at) VALUES(?,?,?,?,?,?,?,0,?,?)",
                envelope.get("eventId"), "AI_TASK", taskId, envelope.get("eventType"), envelope.get("eventType"), jsonValue(envelope), "NEW", now, now);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> callback(Map<String, Object> payload) {
        long taskId = ((Number) payload.get("taskId")).longValue(), ticketId = ((Number) payload.get("ticketId")).longValue();
        var task = jdbc.queryForList("SELECT * FROM ai_task WHERE id=? AND biz_type='TICKET_ANALYSIS' AND biz_id=? FOR UPDATE", taskId, ticketId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI_TASK_NOT_BOUND"));
        String callId = payload.get("callId") instanceof String s ? s : "";
        if ((callId.isBlank() || callId.startsWith("legacy-")) && "SUCCEEDED".equals(task.get("status"))) {
            return Map.of("accepted", true, "duplicate", true);
        }
        if (callId.isBlank() || callId.equals("legacy-" + taskId)) callId = calls.ensureLegacy(taskId, ticketId, MDC.get("traceId"));
        String status = String.valueOf(payload.get("status"));
        if (!Set.of("SUCCEEDED", "FAILED").contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI_STATUS_INVALID");
        Map<String, Object> usage = (Map<String, Object>) payload.getOrDefault("usage", Map.of());
        String error = payload.get("errorCode") instanceof String s ? s : null;
        if (!calls.finish(callId, taskId, ticketId, "TICKET_ANALYSIS", status, usage, error)) return Map.of("accepted", true, "duplicate", true);
        if ("SUCCEEDED".equals(task.get("status"))) return Map.of("accepted", true);
        LocalDateTime now = now();
        if (status.equals("SUCCEEDED")) {
            Map<String, Object> result = (Map<String, Object>) payload.getOrDefault("result", Map.of());
            int version = ((Number) payload.getOrDefault("ticketVersion", 0)).intValue();
            boolean stale = Boolean.TRUE.equals(jdbc.queryForObject("SELECT version<>? FROM service_ticket WHERE id=?", Boolean.class, version, ticketId));
            Map<String, Object> stored = calls.get(callId);
            int inserted = jdbc.update("INSERT IGNORE INTO ai_analysis(id,ticket_id,task_id,ticket_version,intent,confidence,priority_suggestion,sentiment,extracted_json,missing_fields_json,reply_suggestion,proposal_suggestion_json,needs_human,risk_flags_json,citations_json,provider,model,prompt_version,input_tokens,output_tokens,estimated_cost_micros,latency_ms,stale,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    id(), ticketId, taskId, version, result.getOrDefault("intent", "OTHER"), result.getOrDefault("confidence", 0),
                    result.getOrDefault("prioritySuggestion", "MEDIUM"), result.getOrDefault("sentiment", "NEUTRAL"),
                    jsonValue(result.get("extracted")), jsonValue(result.get("missingFields")), result.get("replySuggestion"),
                    jsonValue(result.get("proposalSuggestion")), Boolean.TRUE.equals(result.get("needsHuman")),
                    jsonValue(result.get("riskFlags")), jsonValue(result.get("citations")), usage.get("provider"), usage.get("model"),
                    usage.get("promptVersion"), stored.get("input_tokens"), stored.get("output_tokens"),
                    stored.get("estimated_cost_micros"), stored.get("latency_ms"), stale, now);
            if (inserted > 0) createAiDraftIfSafe(ticketId, result, stale, now);
            jdbc.update("UPDATE ai_task SET status='SUCCEEDED',finished_at=?,updated_at=? WHERE id=?", now, now, taskId);
        } else {
            jdbc.update("UPDATE ai_task SET status='FAILED',error_code=?,error_message=?,finished_at=?,next_retry_at=?,updated_at=? WHERE id=? AND status<>'SUCCEEDED'",
                    error == null ? "AI_FAILED" : error, "AI provider call failed", now, now.plusSeconds(30), now, taskId);
        }
        return Map.of("accepted", true);
    }

    @SuppressWarnings("unchecked")
    private void createAiDraftIfSafe(long ticketId, Map<String, Object> result, boolean stale, LocalDateTime now) {
        if (stale || Boolean.TRUE.equals(result.get("needsHuman")) || result.get("proposalSuggestion") == null) return;
        if (jdbc.queryForObject("SELECT COUNT(*) FROM service_proposal WHERE ticket_id=? AND status IN ('DRAFT','PENDING_CONFIRMATION','CONFIRMED')", Integer.class, ticketId) > 0) return;
        Map<String, Object> suggestion = (Map<String, Object>) result.get("proposalSuggestion");
        String type = String.valueOf(suggestion.getOrDefault("type", result.getOrDefault("intent", "OTHER")));
        if (!Set.of("REFUND_ONLY", "RETURN_REFUND", "EXCHANGE", "REPAIR").contains(type)) return;
        long id = id(), amount = type.equals("REFUND_ONLY") && suggestion.get("suggestedRefundAmountCent") instanceof Number n ? n.longValue() : 0;
        String no = "SP" + now.toLocalDate().toString().replace("-", "") + String.format("%06d", Math.floorMod(id, 1000000));
        jdbc.update("INSERT INTO service_proposal(id,proposal_no,ticket_id,type,source,status,requested_amount_cent,refund_amount_cent,reason_code,description,conditions_json,created_by,expires_at,version,created_at,updated_at) VALUES(?,?,?,?,?,'DRAFT',?,?,?,?,?,NULL,?,0,?,?)",
                id, no, ticketId, type, "AI", amount, amount, suggestion.get("reasonCode"),
                suggestion.getOrDefault("description", "AI 建议售后方案，待客服审核"), jsonValue(suggestion.getOrDefault("conditions", Map.of())), now.plusHours(24), now, now);
    }

    @Override public Object latestAnalysis(long ticketId) {
        return jdbc.queryForList("SELECT * FROM ai_analysis WHERE ticket_id=? ORDER BY created_at DESC LIMIT 1", ticketId)
                .stream().findFirst().orElse(Map.of("status", "NOT_FOUND"));
    }

    @Override @Transactional
    public Object retryTicketAnalysis(long ticketId, long operatorId) {
        var task = jdbc.queryForList("SELECT * FROM ai_task WHERE biz_type='TICKET_ANALYSIS' AND biz_id=? ORDER BY created_at DESC LIMIT 1 FOR UPDATE", ticketId).stream().findFirst().orElseThrow();
        int attempts = ((Number) task.get("attempt_count")).intValue(), max = ((Number) task.get("max_attempts")).intValue();
        if (!"FAILED".equals(task.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "AI_TASK_NOT_FAILED");
        if (attempts >= max) throw new ResponseStatusException(HttpStatus.CONFLICT, "AI_TASK_MAX_ATTEMPTS");
        long taskId = ((Number) task.get("id")).longValue();
        LocalDateTime now = now();
        int version = jdbc.queryForObject("SELECT version FROM service_ticket WHERE id=?", Integer.class, ticketId);
        jdbc.update("UPDATE ai_task SET status='PENDING',attempt_count=attempt_count+1,error_code=NULL,error_message=NULL,next_retry_at=NULL,updated_at=? WHERE id=?", now, taskId);
        enqueue(taskId, ticketId, version, now);
        return Map.of("taskId", taskId, "status", "PENDING", "attemptCount", attempts + 1);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> documentCallback(Map<String, Object> payload) {
        long documentId = ((Number) payload.get("documentId")).longValue(), taskId = ((Number) payload.get("taskId")).longValue();
        String status = String.valueOf(payload.getOrDefault("status", "FAILED"));
        var tasks = jdbc.queryForList("SELECT status FROM ai_task WHERE id=? AND biz_type='DOCUMENT_INDEX' AND biz_id=? FOR UPDATE", taskId, documentId);
        if (tasks.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI_TASK_NOT_BOUND");
        if ("SUCCEEDED".equals(tasks.getFirst().get("status"))) return Map.of("accepted", true);
        LocalDateTime now = now();
        if ("SUCCEEDED".equals(status)) {
            var chunks = (List<Map<String, Object>>) payload.getOrDefault("chunks", List.of());
            jdbc.update("UPDATE knowledge_document SET status='INDEXED',chunk_count=?,updated_at=? WHERE id=?", chunks.size(), now, documentId);
            for (var chunk : chunks) jdbc.update("INSERT IGNORE INTO knowledge_chunk_meta(id,document_id,chunk_id,index_version,sequence_no,section_title,content_hash,token_count,qdrant_point_id,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    id(), documentId, chunk.get("chunkId"), payload.getOrDefault("indexVersion", 1), chunk.getOrDefault("sequenceNo", 0),
                    chunk.get("sectionTitle"), chunk.get("contentHash"), null, chunk.get("qdrantPointId"), now);
        } else jdbc.update("UPDATE knowledge_document SET status='FAILED',error_message=?,updated_at=? WHERE id=?", "Document indexing failed", now, documentId);
        jdbc.update("UPDATE ai_task SET status=?,finished_at=?,updated_at=? WHERE id=?", status, now, now, taskId);
        return Map.of("accepted", true);
    }

    private long id() { return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE; }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private String jsonValue(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("AI payload serialization failed", e); }
    }
}
