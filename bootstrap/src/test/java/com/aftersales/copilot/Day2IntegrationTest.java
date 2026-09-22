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
class Day2IntegrationTest {
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

    @Test
    void migrationsAndDemoSeedAreApplied() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class)).isEqualTo(6);
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
