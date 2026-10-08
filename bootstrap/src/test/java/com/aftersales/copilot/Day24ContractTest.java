package com.aftersales.copilot;

import com.aftersales.copilot.aiadapter.application.AiTaskApplicationService;
import com.aftersales.copilot.statistics.application.AiCallService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Day24ContractTest {
    private final ObjectMapper json = new ObjectMapper();
    private Map<String, Object> fixture(String name) throws Exception {
        Path root = Path.of(System.getProperty("maven.multiModuleProjectDirectory", ".."));
        return json.readValue(Files.readString(root.resolve("contracts").resolve(name)), new TypeReference<>() {});
    }

    @Test void actualProducerMatchesSharedEnvelopeShapeAndPreservesLongIds() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var calls = mock(AiCallService.class);
        var service = new AiTaskApplicationService(jdbc, json, calls);
        long ticketId = 9007199254740993L;
        service.scheduleTicketAnalysis(ticketId, 1, 42L);
        ArgumentCaptor<Object[]> updates = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(anyString(), updates.capture());
        var actual = json.readTree((String) updates.getAllValues().getLast()[5]);
        var expected = json.valueToTree(fixture("ticket-analysis-request.json"));
        assertThat(actual.properties().stream().map(Map.Entry::getKey)).containsExactlyInAnyOrderElementsOf(expected.properties().stream().map(Map.Entry::getKey).toList());
        assertThat(actual.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(actual.path("eventType").asText()).isEqualTo(expected.path("eventType").asText());
        assertThat(actual.path("producer").asText()).isEqualTo("aftersales-server");
        assertThat(actual.path("data").path("ticketId").asLong()).isEqualTo(ticketId);
        assertThat(actual.path("data").properties().stream().map(Map.Entry::getKey)).containsExactlyInAnyOrderElementsOf(expected.path("data").properties().stream().map(Map.Entry::getKey).toList());
        verify(calls).begin(anyString(), anyLong(), eq(ticketId), eq("TICKET_ANALYSIS"), anyString());
    }

    @Test void sharedPythonCallbackIsConsumedWithoutCreatingUnsafeProposal() throws Exception {
        var body = fixture("ticket-analysis-callback.json");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var calls = mock(AiCallService.class);
        long taskId = ((Number) body.get("taskId")).longValue();
        assertThat(taskId).isEqualTo(9007199254740993L);
        when(jdbc.queryForList(contains("FROM ai_task"), eq(taskId), eq(60001L))).thenReturn(List.of(Map.of("status", "PENDING")));
        when(calls.finish(eq("contract-call-v1"), eq(taskId), eq(60001L), eq("TICKET_ANALYSIS"), eq("SUCCEEDED"), anyMap(), isNull())).thenReturn(true);
        when(calls.get("contract-call-v1")).thenReturn(Map.of());
        when(jdbc.queryForObject(contains("SELECT version<>"), eq(Boolean.class), eq(1), eq(60001L))).thenReturn(false);
        when(jdbc.update(startsWith("INSERT IGNORE INTO ai_analysis"), any(Object[].class))).thenReturn(1);
        var result = new AiTaskApplicationService(jdbc, json, calls).callback(body);
        assertThat(result).containsEntry("accepted", true);
        verify(jdbc).update(startsWith("INSERT IGNORE INTO ai_analysis"), any(Object[].class));
        verify(jdbc, never()).update(startsWith("INSERT INTO service_proposal"), any(Object[].class));
    }

    @Test void riskFlagsBlockDraftEvenWhenProviderClaimsNoHumanNeeded() throws Exception {
        var body = fixture("ticket-analysis-callback.json");
        @SuppressWarnings("unchecked")
        var result = (Map<String, Object>) body.get("result");
        result.put("needsHuman", false);
        result.put("riskFlags", List.of("PROMPT_INJECTION"));
        result.put("proposalSuggestion", Map.of("type", "REFUND_ONLY", "suggestedRefundAmountCent", 100));
        result.put("citations", List.of(Map.of("chunkId", "c1")));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var calls = mock(AiCallService.class);
        long taskId = ((Number) body.get("taskId")).longValue();
        when(jdbc.queryForList(contains("FROM ai_task"), eq(taskId), eq(60001L))).thenReturn(List.of(Map.of("status", "PENDING")));
        when(calls.finish(anyString(), eq(taskId), eq(60001L), eq("TICKET_ANALYSIS"), eq("SUCCEEDED"), anyMap(), isNull())).thenReturn(true);
        when(calls.get(anyString())).thenReturn(Map.of());
        when(jdbc.queryForObject(contains("SELECT version<>"), eq(Boolean.class), eq(1), eq(60001L))).thenReturn(false);
        when(jdbc.update(startsWith("INSERT IGNORE INTO ai_analysis"), any(Object[].class))).thenReturn(1);
        new AiTaskApplicationService(jdbc, json, calls).callback(body);
        verify(jdbc, never()).update(startsWith("INSERT INTO service_proposal"), any(Object[].class));
    }
}
