package com.aftersales.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@org.springframework.context.annotation.Import(Day2IntegrationTest.AsyncSecurityController.class)
class Day2IntegrationTest {
    @org.springframework.web.bind.annotation.RestController
    static class AsyncSecurityController {
        @org.springframework.web.bind.annotation.GetMapping(value="/api/v1/test/async-security", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
        reactor.core.publisher.Flux<org.springframework.http.codec.ServerSentEvent<String>> stream() {
            return reactor.core.publisher.Flux.just(
                    org.springframework.http.codec.ServerSentEvent.builder("first").event("token").build(),
                    org.springframework.http.codec.ServerSentEvent.builder("complete").event("done").build())
                    .delayElements(java.time.Duration.ofMillis(20));
        }
    }

    @Test
    void day23AsyncStreamRetainsAuthenticationAndRejectsAnonymousRequest() throws Exception {
        String path = "/api/v1/test/async-security";
        assertThat(restTemplate.getForEntity(url(path), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String token = objectMapper.readTree(post("/api/v1/auth/login", Map.of("username", "customer01", "password", "Demo@123456")).getBody())
                .path("data").path("accessToken").asText();
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token);
        var response = restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("event:token", "data:first", "event:done", "data:complete");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.40")
            .withDatabaseName("aftersales_test")
            .withUsername("aftersales")
            .withPassword("aftersales_test_password")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.default-schema", MYSQL::getDatabaseName);
        registry.add("app.security.jwt-secret", () -> "test-only-jwt-secret-at-least-32-bytes-long");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    com.aftersales.copilot.statistics.application.AiCallService calls;

    @Autowired
    com.aftersales.copilot.aiadapter.application.AiTaskApplicationService aiTasks;

    @Test
    void day23LegacyCallbackKeepsCostUnknownAndDeduplicates() throws Exception {
        String token = objectMapper.readTree(post("/api/v1/auth/login", Map.of("username", "customer01", "password", "Demo@123456")).getBody())
                .path("data").path("accessToken").asText();
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token); headers.setContentType(MediaType.APPLICATION_JSON);
        var response = restTemplate.postForEntity(url("/api/v1/tickets"), new HttpEntity<>(Map.of("orderItemId", 3101,
                "requestedType", "REFUND_ONLY", "title", "Legacy usage", "description", "Legacy callback regression",
                "clientRequestId", java.util.UUID.randomUUID().toString()), headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var ticket = objectMapper.readTree(response.getBody()).path("data");
        long ticketId = ticket.path("id").asLong(), taskId = 12345L;
        jdbcTemplate.update("INSERT INTO ai_task(id,biz_type,biz_id,dedup_key,status,created_at,updated_at) VALUES(?,'TICKET_ANALYSIS',?,?,'PENDING',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                taskId, ticketId, "legacy-regression:" + taskId);
        Map<String,Object> payload = new java.util.HashMap<>(Map.of("taskId", taskId, "ticketId", ticketId,
                "ticketVersion", ticket.path("version").asInt(), "status", "SUCCEEDED", "result", Map.of("needsHuman", true),
                "usage", Map.of("provider", "fake", "model", "fake-v1", "estimatedCostMicros", 0)));
        assertThat(aiTasks.callback(payload).get("accepted")).isEqualTo(true);
        assertThat(aiTasks.callback(payload).get("duplicate")).isEqualTo(true);
        payload.put("callId", "legacy-" + taskId);
        assertThat(aiTasks.callback(payload).get("duplicate")).isEqualTo(true);
        var row = jdbcTemplate.queryForMap("SELECT * FROM ai_call_log WHERE call_id=?", "legacy-" + taskId);
        assertThat(row.get("estimated_cost_micros")).isNull();
        assertThat(row.get("input_tokens")).isNull();
        assertThat(row.get("cost_status")).isEqualTo("HISTORICAL_UNKNOWN");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_analysis WHERE task_id=?", Integer.class, taskId)).isEqualTo(1);
    }

    @Test
    void day23CostCallbackIsIdempotentAndRecomputesFakeUsage() {
        String callId = java.util.UUID.randomUUID().toString();
        calls.begin(callId, null, 42L, "CHAT", "integration-trace");
        Map<String,Object> usage = Map.of("provider", "fake", "model", "fake-v1", "inputTokens", 999,
                "outputTokens", 999, "estimatedCostMicros", 999999, "latencyMs", 10);
        assertThat(calls.finish(callId, null, 42L, "CHAT", "SUCCEEDED", usage, null)).isTrue();
        assertThat(calls.finish(callId, null, 42L, "CHAT", "SUCCEEDED", usage, null)).isFalse();
        var row = jdbcTemplate.queryForMap("SELECT * FROM ai_call_log WHERE call_id=?", callId);
        assertThat(((Number)row.get("estimated_cost_micros")).longValue()).isZero();
        assertThat(row.get("input_tokens")).isNull();
        assertThat(row.get("status")).isEqualTo("SUCCEEDED");
    }

    @Test
    void day23DashboardAndPrometheusRequireAdministrator() throws Exception {
        String customer = objectMapper.readTree(post("/api/v1/auth/login", Map.of("username","customer01", "password","Demo@123456")).getBody())
                .path("data").path("accessToken").asText();
        String admin = objectMapper.readTree(post("/api/v1/auth/login", Map.of("username","admin01", "password","Demo@123456")).getBody())
                .path("data").path("accessToken").asText();
        HttpHeaders customerHeaders = new HttpHeaders(); customerHeaders.setBearerAuth(customer);
        HttpHeaders adminHeaders = new HttpHeaders(); adminHeaders.setBearerAuth(admin);
        for (String path : new String[]{"/api/v1/admin/dashboard/overview", "/api/v1/admin/statistics/ai-usage", "/actuator/prometheus"}) {
            assertThat(restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(customerHeaders), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            var response = restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        var invalid = restTemplate.exchange(url("/api/v1/admin/statistics/ai-usage?groupBy=model"), HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void migrationsAndDemoSeedAreApplied() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class)).isEqualTo(12);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer_order", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM logistics_shipment", Integer.class)).isEqualTo(3);
    }

    @Test
    void loginReturnsTokensAndAccessTokenAuthenticatesMe() throws Exception {
        ResponseEntity<String> login = post("/api/v1/auth/login", Map.of(
                "username", "customer01",
                "password", "Demo@123456"));

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode loginJson = objectMapper.readTree(login.getBody());
        assertThat(loginJson.path("code").asText()).isEqualTo("OK");
        assertThat(loginJson.path("data").path("expiresIn").asLong()).isEqualTo(900);
        assertThat(loginJson.path("data").path("user").path("role").asText()).isEqualTo("CUSTOMER");

        String accessToken = loginJson.path("data").path("accessToken").asText();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<String> me = restTemplate.exchange(url("/api/v1/auth/me"), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(me.getBody()).path("data").path("username").asText()).isEqualTo("customer01");
    }

    @Test
    void loginAcceptsEmailAndInvalidCredentialsUseSameError() throws Exception {
        assertThat(post("/api/v1/auth/login", Map.of(
                "username", "customer01@example.test",
                "password", "Demo@123456")).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> wrongPassword = post("/api/v1/auth/login", Map.of(
                "username", "customer01",
                "password", "wrong-password"));
        ResponseEntity<String> missingUser = post("/api/v1/auth/login", Map.of(
                "username", "not-exists",
                "password", "wrong-password"));

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(missingUser.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(objectMapper.readTree(wrongPassword.getBody()).path("code").asText()).isEqualTo("AUTH_INVALID_CREDENTIALS");
        assertThat(objectMapper.readTree(missingUser.getBody()).path("code").asText()).isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void refreshRotatesTokenAndOldTokenCannotBeReused() throws Exception {
        JsonNode login = objectMapper.readTree(post("/api/v1/auth/login", Map.of(
                "username", "customer01",
                "password", "Demo@123456")).getBody());
        String oldRefreshToken = login.path("data").path("refreshToken").asText();

        ResponseEntity<String> refreshed = post("/api/v1/auth/refresh", Map.of("refreshToken", oldRefreshToken));
        ResponseEntity<String> replay = post("/api/v1/auth/refresh", Map.of("refreshToken", oldRefreshToken));

        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(objectMapper.readTree(replay.getBody()).path("code").asText()).isEqualTo("AUTH_INVALID_REFRESH_TOKEN");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_refresh_token WHERE revoked_at IS NOT NULL", Integer.class))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        JsonNode login = objectMapper.readTree(post("/api/v1/auth/login", Map.of(
                "username", "customer01",
                "password", "Demo@123456")).getBody());
        String refreshToken = login.path("data").path("refreshToken").asText();

        assertThat(post("/api/v1/auth/logout", Map.of("refreshToken", refreshToken)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<String> post(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(url(path), new HttpEntity<>(body, headers), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
