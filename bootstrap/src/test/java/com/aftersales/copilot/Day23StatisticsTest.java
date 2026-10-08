package com.aftersales.copilot;

import com.aftersales.copilot.common.observability.TraceIds;
import com.aftersales.copilot.statistics.application.AiCallService;
import com.aftersales.copilot.statistics.application.AiPricing;
import com.aftersales.copilot.statistics.application.StatisticsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Day23StatisticsTest {
    private ObjectMapper mapper() { return new ObjectMapper().registerModule(new JavaTimeModule()); }
    private String prices() {
        return "[{\"provider\":\"test\",\"model\":\"test-v1\",\"inputCnyPerMillion\":0.1,\"outputCnyPerMillion\":0.2,\"currency\":\"CNY\",\"effectiveFrom\":\"2026-10-01\",\"sourceUrl\":\"https://example.com/pricing\",\"verified\":true}]";
    }

    @Test void costsUseVerifiedEffectivePricesAndRoundUpWithoutFloatingPoint() {
        var pricing = new AiPricing(mapper(), prices());
        var estimate = pricing.estimate("test", "test-v1", 3L, 5L, LocalDate.of(2026, 10, 8));
        assertThat(estimate.micros()).isEqualTo(2);
        assertThat(estimate.status()).isEqualTo("ESTIMATED");
        assertThat(pricing.estimate("test", "test-v1", 3L, 5L, LocalDate.of(2026, 9, 30)).micros()).isNull();
        assertThat(pricing.estimate("test", "test-v1", null, 5L, LocalDate.of(2026, 10, 8)).status()).isEqualTo("UNKNOWN_USAGE");
        assertThat(pricing.estimate("other", "unknown", 3L, 5L, LocalDate.of(2026, 10, 8)).status()).isEqualTo("UNKNOWN_PRICE");
        assertThat(pricing.estimate("fake", "fake-v1", null, null, LocalDate.of(2026, 10, 8)).micros()).isZero();
        var unverified = new AiPricing(mapper(), prices().replace("\"verified\":true", "\"verified\":false"));
        assertThat(unverified.estimate("test", "test-v1", 3L, 5L, LocalDate.of(2026, 10, 8)).micros()).isNull();
    }

    @Test void invalidPriceConfigurationIsRejected() {
        assertThatThrownBy(() -> new AiPricing(mapper(), prices().replace("\"CNY\"", "\"USD\""))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiPricing(mapper(), prices().replace("0.1", "-0.1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void statisticsRangeIsShanghaiInclusiveAndBounded() {
        var range = StatisticsService.range(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 8));
        assertThat(range.startUtc()).isEqualTo(LocalDateTime.of(2026, 10, 7, 16, 0));
        assertThat(range.endUtc()).isEqualTo(LocalDateTime.of(2026, 10, 8, 16, 0));
        assertThatThrownBy(() -> StatisticsService.range(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 7))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> StatisticsService.range(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2))).isInstanceOf(ResponseStatusException.class);
    }

    @Test void traceIdsRejectHeaderInjectionAndExcessiveLength() {
        assertThat(TraceIds.normalize("trace-123_A")).isEqualTo("trace-123_A");
        assertThat(TraceIds.normalize("injected\r\nheader")).matches("[A-Za-z0-9_-]{1,64}");
        assertThat(TraceIds.normalize("x".repeat(65))).hasSize(36);
    }

    @Test void callbackRecomputesCostAndDuplicateDoesNotUpdateAgain() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new AiCallService(jdbc, new AiPricing(mapper(), prices()));
        var row = new HashMap<String, Object>();
        row.put("task_id", 1L); row.put("ticket_id", 2L); row.put("operation", "TICKET_ANALYSIS");
        row.put("status", "PENDING"); row.put("created_at", LocalDateTime.of(2026, 10, 8, 0, 0)); row.put("trace_id", "test-trace");
        when(jdbc.queryForList(anyString(), eq("call"))).thenReturn(List.of(row));
        Map<String, Object> usage = Map.of("provider", "test", "model", "test-v1", "inputTokens", 3, "outputTokens", 5, "estimatedCostMicros", 9999999, "latencyMs", 123);
        assertThat(service.finish("call", 1L, 2L, "TICKET_ANALYSIS", "SUCCEEDED", usage, null)).isTrue();
        verify(jdbc).update(startsWith("UPDATE ai_call_log SET provider="), eq("test"), eq("test-v1"), isNull(), eq(3L), eq(5L), eq(2L), eq("ESTIMATED"), eq("https://example.com/pricing"), eq(123L), eq("SUCCEEDED"), isNull(), any(LocalDateTime.class), eq("call"));
        row.put("status", "SUCCEEDED");
        assertThat(service.finish("call", 1L, 2L, "TICKET_ANALYSIS", "SUCCEEDED", usage, null)).isFalse();
        verify(jdbc, times(1)).update(anyString(), any(Object[].class));
    }

    @Test void callbackRejectsMismatchedTicketAndNegativeUsage() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new AiCallService(jdbc, new AiPricing(mapper(), "[]"));
        when(jdbc.queryForList(anyString(), eq("call"))).thenReturn(List.of(Map.of("task_id", 1L, "ticket_id", 2L, "operation", "TICKET_ANALYSIS", "status", "PENDING")));
        assertThatThrownBy(() -> service.finish("call", 1L, 3L, "TICKET_ANALYSIS", "SUCCEEDED", Map.of(), null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.finish("call", 1L, 2L, "TICKET_ANALYSIS", "SUCCEEDED", Map.of("inputTokens", -1), null)).isInstanceOf(ResponseStatusException.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void dailySeriesFillsMissingDaysAndPreservesUnknownCosts() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new StatisticsService(jdbc, 2000000, 40000000, 50000000);
        when(jdbc.queryForMap(anyString(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(new HashMap<>(Map.of("calls", 1L, "unknownCostCalls", 1L, "knownCostMicros", 0L)));
        when(jdbc.queryForList(contains("GROUP BY day"), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of(new HashMap<>(Map.of("day", "2026-10-08", "calls", 1L, "unknownCostCalls", 1L, "knownCostMicros", 0L))));
        when(jdbc.queryForList(contains("GROUP BY provider,model"), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of());
        var result = service.usage(StatisticsService.range(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 8)));
        var days = (List<Map<String, Object>>) result.get("days");
        assertThat(days).hasSize(2);
        assertThat(days.getFirst().get("calls")).isEqualTo(0L);
        assertThat(days.getLast().get("unknownCostCalls")).isEqualTo(1L);
        assertThat(result.get("timezone")).isEqualTo("Asia/Shanghai");
    }
}
