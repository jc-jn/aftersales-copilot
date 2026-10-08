package com.aftersales.copilot.statistics.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class StatisticsService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public record Range(LocalDate from, LocalDate to) {
        public LocalDateTime startUtc() { return LocalDateTime.ofInstant(from.atStartOfDay(ZONE).toInstant(), ZoneOffset.UTC); }
        public LocalDateTime endUtc() { return LocalDateTime.ofInstant(to.plusDays(1).atStartOfDay(ZONE).toInstant(), ZoneOffset.UTC); }
    }
    private final JdbcTemplate jdbc;
    private final long dailyLimit, projectWarning, projectLimit;

    public StatisticsService(JdbcTemplate jdbc, @Value("${app.ai.budget.daily-soft-limit-micros:2000000}") long dailyLimit,
                             @Value("${app.ai.budget.project-warning-micros:40000000}") long projectWarning,
                             @Value("${app.ai.budget.project-hard-limit-micros:50000000}") long projectLimit) {
        this.jdbc = jdbc; this.dailyLimit = dailyLimit; this.projectWarning = projectWarning; this.projectLimit = projectLimit;
        if (dailyLimit <= 0 || projectWarning <= 0 || projectLimit < projectWarning) throw new IllegalArgumentException("Invalid AI budget thresholds");
    }

    public static Range range(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(ZONE) : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) >= 366) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "STATISTICS_RANGE_INVALID");
        }
        return new Range(start, end);
    }

    private static final String SUMMARY = "COUNT(*) calls, SUM(status='SUCCEEDED') succeeded, SUM(status='FAILED') failed, SUM(status='INTERRUPTED') interrupted, SUM(status='PENDING') pending, SUM(status='HISTORICAL') historical, COALESCE(SUM(input_tokens),0) inputTokens, COALESCE(SUM(output_tokens),0) outputTokens, SUM(input_tokens IS NULL OR output_tokens IS NULL) unknownTokenCalls, COALESCE(SUM(estimated_cost_micros),0) knownCostMicros, SUM(estimated_cost_micros IS NULL) unknownCostCalls, AVG(latency_ms) avgLatencyMs";

    public Map<String, Object> usage(Range range) {
        var summary = jdbc.queryForMap("SELECT " + SUMMARY + " FROM ai_call_log WHERE created_at>=? AND created_at<?", range.startUtc(), range.endUtc());
        var rows = jdbc.queryForList("SELECT DATE(DATE_ADD(created_at, INTERVAL 8 HOUR)) day, " + SUMMARY + " FROM ai_call_log WHERE created_at>=? AND created_at<? GROUP BY day ORDER BY day", range.startUtc(), range.endUtc());
        var days = new HashMap<String, Map<String, Object>>();
        for (var row : rows) days.put(row.get("day").toString(), normalize(row));
        var trend = new ArrayList<Map<String, Object>>();
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
            var point = days.getOrDefault(day.toString(), emptySummary());
            point.put("day", day.toString()); trend.add(point);
        }
        return Map.of("from", range.from().toString(), "to", range.to().toString(), "timezone", ZONE.toString(),
                "currency", "CNY", "summary", normalize(summary), "days", trend,
                "models", jdbc.queryForList("SELECT COALESCE(provider,'unknown') provider,COALESCE(model,'unknown') model," + SUMMARY + " FROM ai_call_log WHERE created_at>=? AND created_at<? GROUP BY provider,model ORDER BY calls DESC LIMIT 100", range.startUtc(), range.endUtc()));
    }

    public Map<String, Object> overview(Range range) {
        return Map.of("from", range.from().toString(), "to", range.to().toString(), "timezone", ZONE.toString(),
                "tickets", states("service_ticket", range), "tasks", states("ai_task", range),
                "documents", states("knowledge_document", range),
                "refunds", jdbc.queryForMap("SELECT COUNT(*) count,COALESCE(SUM(amount_cent),0) amountCent FROM refund_record WHERE status='SUCCEEDED' AND created_at>=? AND created_at<?", range.startUtc(), range.endUtc()),
                "outbox", jdbc.queryForMap("SELECT COUNT(*) pending,COALESCE(SUM(status='FAILED'),0) failed,COALESCE(MAX(TIMESTAMPDIFF(SECOND,created_at,UTC_TIMESTAMP())),0) oldestAgeSeconds FROM outbox_event WHERE status<>'PUBLISHED'"),
                "budget", budget());
    }

    public Map<String, Object> budget() {
        var project = jdbc.queryForMap("SELECT COALESCE(SUM(estimated_cost_micros),0) knownCostMicros,COUNT(*)-COUNT(estimated_cost_micros) unknownCostCalls FROM ai_call_log");
        var todayRange = range(LocalDate.now(ZONE), LocalDate.now(ZONE));
        var today = jdbc.queryForMap("SELECT COALESCE(SUM(estimated_cost_micros),0) knownCostMicros,COUNT(*)-COUNT(estimated_cost_micros) unknownCostCalls FROM ai_call_log WHERE created_at>=? AND created_at<?", todayRange.startUtc(), todayRange.endUtc());
        long total = ((Number) project.get("knownCostMicros")).longValue(), day = ((Number) today.get("knownCostMicros")).longValue();
        return Map.of("currency", "CNY", "today", today, "project", project,
                "dailySoftLimitMicros", dailyLimit, "projectWarningMicros", projectWarning, "projectHardLimitMicros", projectLimit,
                "dailyWarning", day >= dailyLimit, "projectWarning", total >= projectWarning,
                "projectLimitReached", total >= projectLimit, "enforced", false);
    }

    private List<Map<String, Object>> states(String table, Range range) {
        return jdbc.queryForList("SELECT status,COUNT(*) count FROM " + table + " WHERE created_at>=? AND created_at<? GROUP BY status ORDER BY count DESC", range.startUtc(), range.endUtc());
    }
    private Map<String, Object> normalize(Map<String, Object> source) {
        var result = new LinkedHashMap<>(source);
        for (String key : emptySummary().keySet()) if (!key.equals("avgLatencyMs") && result.get(key) == null) result.put(key, 0L);
        return result;
    }
    private Map<String, Object> emptySummary() {
        var result = new LinkedHashMap<String, Object>();
        for (String key : List.of("calls", "succeeded", "failed", "interrupted", "pending", "historical", "inputTokens", "outputTokens", "unknownTokenCalls", "knownCostMicros", "unknownCostCalls")) result.put(key, 0L);
        result.put("avgLatencyMs", null);
        return result;
    }
}
