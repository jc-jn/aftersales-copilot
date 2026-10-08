package com.aftersales.copilot.statistics.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class OperationalMetrics {
    private static final Logger log = LoggerFactory.getLogger(OperationalMetrics.class);
    private final JdbcTemplate jdbc;
    private final Map<String, AtomicReference<Double>> values = new LinkedHashMap<>();

    public OperationalMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        for (String name : new String[]{"outbox.pending", "outbox.failed", "outbox.oldest.age.seconds", "ai.calls.pending",
                "ai.calls.succeeded", "ai.calls.failed", "ai.calls.unknown.cost", "ai.cost.known.micros", "ai.tasks.failed", "proposal.executions.failed"}) {
            var value = new AtomicReference<>(Double.NaN);
            values.put(name, value);
            Gauge.builder("aftersales." + name, value, v -> v.get()).register(registry);
        }
        var up = new AtomicReference<>(0.0);
        values.put("observability.up", up);
        Gauge.builder("aftersales.observability.up", up, v -> v.get()).register(registry);
    }

    @Scheduled(fixedDelayString="${app.observability.refresh-ms:30000}", initialDelayString="${app.observability.initial-delay-ms:1000}")
    public void refresh() {
        try {
            var outbox = jdbc.queryForMap("SELECT COUNT(*) pending,COALESCE(SUM(status='FAILED'),0) failed,COALESCE(MAX(TIMESTAMPDIFF(SECOND,created_at,UTC_TIMESTAMP())),0) age FROM outbox_event WHERE status<>'PUBLISHED'");
            var calls = jdbc.queryForMap("SELECT COALESCE(SUM(status='PENDING'),0) pending,COALESCE(SUM(status='SUCCEEDED'),0) succeeded,COALESCE(SUM(status='FAILED'),0) failed,COUNT(*)-COUNT(estimated_cost_micros) unknownCost,COALESCE(SUM(estimated_cost_micros),0) knownCost FROM ai_call_log");
            set("outbox.pending", outbox.get("pending")); set("outbox.failed", outbox.get("failed")); set("outbox.oldest.age.seconds", outbox.get("age"));
            set("ai.calls.pending", calls.get("pending")); set("ai.calls.succeeded", calls.get("succeeded")); set("ai.calls.failed", calls.get("failed"));
            set("ai.calls.unknown.cost", calls.get("unknownCost")); set("ai.cost.known.micros", calls.get("knownCost"));
            set("ai.tasks.failed", jdbc.queryForObject("SELECT COUNT(*) FROM ai_task WHERE status='FAILED'", Long.class));
            set("proposal.executions.failed", jdbc.queryForObject("SELECT COUNT(*) FROM proposal_execution WHERE status='FAILED'", Long.class));
            set("observability.up", 1);
        } catch (RuntimeException e) {
            values.forEach((name, value) -> value.set(name.equals("observability.up") ? 0.0 : Double.NaN));
            log.atWarn().addKeyValue("event", "metrics_refresh_failed").addKeyValue("errorType", e.getClass().getSimpleName()).log("Operational metrics unavailable");
        }
    }
    private void set(String name, Object value) { values.get(name).set(value instanceof Number n ? n.doubleValue() : Double.NaN); }
}
