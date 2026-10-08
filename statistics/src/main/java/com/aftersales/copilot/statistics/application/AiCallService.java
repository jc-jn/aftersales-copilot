package com.aftersales.copilot.statistics.application;

import com.aftersales.copilot.common.observability.TraceIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

@Service
public class AiCallService {
    private static final Logger log = LoggerFactory.getLogger(AiCallService.class);
    private final JdbcTemplate jdbc;
    private final AiPricing pricing;

    public AiCallService(JdbcTemplate jdbc, AiPricing pricing) { this.jdbc = jdbc; this.pricing = pricing; }

    public void begin(String callId, Long taskId, Long ticketId, String operation, String traceId) {
        jdbc.update("INSERT INTO ai_call_log(call_id,trace_id,task_id,ticket_id,operation,status,created_at) VALUES(?,?,?,?,?,'PENDING',?)",
                callId, TraceIds.normalize(traceId), taskId, ticketId, operation, LocalDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public boolean finish(String callId, Long taskId, Long ticketId, String operation,
                          String status, Map<String, Object> usage, String errorCode) {
        if (status == null || !Set.of("SUCCEEDED", "FAILED", "INTERRUPTED").contains(status)
                || callId == null || !callId.matches("[A-Za-z0-9_-]{1,64}")) throw invalid();
        var call = jdbc.queryForList("SELECT * FROM ai_call_log WHERE call_id=? FOR UPDATE", callId)
                .stream().findFirst().orElseThrow(AiCallService::invalid);
        if (!java.util.Objects.equals(number(call.get("task_id")), taskId)
                || !java.util.Objects.equals(number(call.get("ticket_id")), ticketId)
                || !operation.equals(call.get("operation"))) throw invalid();
        if (!"PENDING".equals(call.get("status"))) return false;
        String provider = label(usage.get("provider"), 64), model = label(usage.get("model"), 128);
        Long input = nonNegative(usage.get("inputTokens")), output = nonNegative(usage.get("outputTokens"));
        Long latency = nonNegative(usage.get("latencyMs"));
        if ("fake".equals(provider)) { input = null; output = null; }
        Object created = call.get("created_at");
        var date = (created instanceof LocalDateTime time ? time : ((java.sql.Timestamp) created).toLocalDateTime()).toLocalDate();
        var estimate = callId.startsWith("legacy-") ? new AiPricing.Estimate(null, "HISTORICAL_UNKNOWN", null)
                : pricing.estimate(provider, model, input, output, date);
        jdbc.update("UPDATE ai_call_log SET provider=?,model=?,prompt_version=?,input_tokens=?,output_tokens=?,estimated_cost_micros=?,cost_status=?,price_source=?,latency_ms=?,status=?,error_code=?,finished_at=? WHERE call_id=? AND status='PENDING'",
                provider, model, label(usage.get("promptVersion"), 64), input, output, estimate.micros(),
                estimate.status(), estimate.source(), latency, status, label(errorCode, 64), LocalDateTime.now(ZoneOffset.UTC), callId);
        log.atInfo().addKeyValue("event", "ai_call_completed").addKeyValue("callId", callId)
                .addKeyValue("traceId", call.get("trace_id")).addKeyValue("operation", operation)
                .addKeyValue("status", status).addKeyValue("costStatus", estimate.status()).log("AI call recorded");
        return true;
    }

    @Transactional
    public void failPending(String callId, String status, String errorCode) {
        jdbc.update("UPDATE ai_call_log SET status=?,error_code=?,finished_at=? WHERE call_id=? AND status='PENDING'",
                status, errorCode, LocalDateTime.now(ZoneOffset.UTC), callId);
    }

    public Map<String, Object> get(String callId) {
        return jdbc.queryForList("SELECT input_tokens,output_tokens,estimated_cost_micros,latency_ms FROM ai_call_log WHERE call_id=?", callId).getFirst();
    }

    public String ensureLegacy(long taskId, long ticketId, String traceId) {
        String callId = "legacy-" + taskId;
        jdbc.update("INSERT IGNORE INTO ai_call_log(call_id,trace_id,task_id,ticket_id,operation,status,cost_status,created_at) VALUES(?,?,?,?,'TICKET_ANALYSIS','PENDING','HISTORICAL_UNKNOWN',?)",
                callId, TraceIds.normalize(traceId), taskId, ticketId, LocalDateTime.now(ZoneOffset.UTC));
        return callId;
    }

    private static Long number(Object value) { return value instanceof Number n ? n.longValue() : null; }
    private static Long nonNegative(Object value) {
        if (value == null) return null;
        if (!(value instanceof Number n)) throw invalid();
        try {
            long result = new java.math.BigDecimal(n.toString()).longValueExact();
            if (result < 0 || result > 1_000_000_000L) throw invalid();
            return result;
        } catch (ArithmeticException e) { throw invalid(); }
    }
    private static String label(Object value, int max) {
        if (value == null) return null;
        if (!(value instanceof String s) || s.length() > max || !s.matches("[A-Za-z0-9_.:/-]+")) throw invalid();
        return s;
    }
    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI_USAGE_INVALID");
    }
}
