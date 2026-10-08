package com.aftersales.copilot;

import com.aftersales.copilot.auth.application.TicketAccessGuard;
import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import com.aftersales.copilot.auth.domain.UserRole;
import com.aftersales.copilot.auth.infrastructure.RateLimitFilter;
import com.aftersales.copilot.auth.infrastructure.SecurityStateStore;
import com.aftersales.copilot.aiadapter.security.InternalHmacFilter;
import com.aftersales.copilot.aiadapter.security.InternalHmacService;
import com.aftersales.copilot.aiadapter.api.AiChatProxyController;
import com.aftersales.copilot.common.security.HmacSignature;
import com.aftersales.copilot.common.storage.FileValidator;
import com.aftersales.copilot.common.storage.PrivateObjectStorage;
import com.aftersales.copilot.ticket.application.TicketAttachmentService;
import com.aftersales.copilot.auth.infrastructure.SnowflakeIdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Day22SecurityTest {
    private static final String AI_SECRET = "test-ai-secret-with-at-least-32-bytes-1234";
    private static final String JAVA_SECRET = "test-java-secret-with-at-least-32-bytes-1234";
    private AuthenticatedUser user(long id, UserRole role) { return new AuthenticatedUser(id, "test", null, "test", role); }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void attachmentsAllowOwnerAndAssignedAgentButDenyOtherUsersAndUnassignedAgents() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TicketAccessGuard guard = new TicketAccessGuard(jdbc);
        when(jdbc.queryForList(anyString(), eq(10L))).thenReturn(List.of(Map.of("customer_id", 1L, "assigned_agent_id", 2L)));
        guard.requireAccess(user(1, UserRole.CUSTOMER), 10);
        guard.requireAccess(user(2, UserRole.AGENT), 10);
        guard.requireAccess(user(3, UserRole.ADMIN), 10);
        assertThatThrownBy(() -> guard.requireAccess(user(3, UserRole.CUSTOMER), 10)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> guard.requireAccess(user(3, UserRole.AGENT), 10)).isInstanceOf(ResponseStatusException.class);
        when(jdbc.queryForList(anyString(), eq(10L))).thenReturn(List.of(Map.of("customer_id", 1L)));
        assertThatThrownBy(() -> guard.requireAccess(user(2, UserRole.AGENT), 10)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void attachmentDownloadCannotUseAnIdFromAnotherTicket() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TicketAccessGuard guard = mock(TicketAccessGuard.class);
        PrivateObjectStorage storage = mock(PrivateObjectStorage.class);
        when(jdbc.queryForList(anyString(), eq(22L), eq(10L))).thenReturn(List.of());
        var service = new TicketAttachmentService(jdbc, guard, storage, mock(SnowflakeIdGenerator.class));
        assertThatThrownBy(() -> service.download(user(1, UserRole.CUSTOMER), 10, 22)).isInstanceOf(ResponseStatusException.class);
        verify(guard).requireAccess(any(), eq(10L));
        verifyNoInteractions(storage);
    }

    @Test void attachmentRejectsFileBeforeStorage() {
        var storage = mock(PrivateObjectStorage.class);
        var service = new TicketAttachmentService(mock(JdbcTemplate.class), mock(TicketAccessGuard.class), storage, mock(SnowflakeIdGenerator.class));
        assertThatThrownBy(() -> service.upload(user(1, UserRole.CUSTOMER), 10,
                new MockMultipartFile("file", "photo.png", "image/png", "malicious".getBytes()))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(storage);
    }

    @Test void fileValidationChecksHeaderMimeAndPath() {
        assertThat(FileValidator.validate("policy.pdf", "application/pdf", "%PDF-1.7\n".getBytes(), false)).isEqualTo("application/pdf");
        assertThat(FileValidator.validate("policy.md", "text/markdown", "# Policy".getBytes(), true)).isEqualTo("text/markdown");
        assertThatThrownBy(() -> FileValidator.validate("../policy.pdf", "application/pdf", "%PDF-1.7".getBytes(), false)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> FileValidator.validate("fake.pdf", "application/pdf", "<script>".getBytes(), false)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> FileValidator.validate("fake.pdf", "text/html", "%PDF-1.7".getBytes(), false)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> FileValidator.validate("binary.txt", "text/plain", new byte[]{0, 1}, true)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> FileValidator.validate("empty.pdf", "application/pdf", new byte[0], false)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void rateLimitHasRetryAfterAndUsesAuthenticatedUser() throws Exception {
        var store = mock(SecurityStateStore.class);
        when(store.increment(anyString(), any())).thenReturn(1L, 2L);
        var filter = new RateLimitFilter(store, new ObjectMapper(), 1, 1, 1, 1);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user(7, UserRole.CUSTOMER), null, List.of()));
        var req = new MockHttpServletRequest("POST", "/api/v1/tickets/10/attachments");
        req.setServletPath("/api/v1/tickets/10/attachments");
        var first = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(req, first, (r, s) -> called.set(true));
        assertThat(called.get()).isTrue();
        var second = new MockHttpServletResponse();
        filter.doFilter(req, second, (r, s) -> fail("Should not reach controller"));
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getHeader("Retry-After")).isNotBlank();
        assertThat(second.getContentAsString()).contains("RATE_LIMITED");
        verify(store, times(2)).increment(startsWith("upload:user:7:"), eq(Duration.ofSeconds(60)));
    }

    @Test void rateLimitFailsClosedWhenRedisIsUnavailable() throws Exception {
        var store = mock(SecurityStateStore.class);
        when(store.increment(anyString(), any())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        var filter = new RateLimitFilter(store, new ObjectMapper(), 1, 1, 1, 1);
        var req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.setServletPath("/api/v1/auth/login");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, (r, s) -> fail("Should not reach controller"));
        assertThat(res.getStatus()).isEqualTo(503);
    }

    @Test void hmacRejectsWrongIdentityAndDoesNotConsumeNonceForBadSignature() {
        var store = mock(SecurityStateStore.class);
        var hmac = new InternalHmacService(AI_SECRET, JAVA_SECRET, store);
        long ts = System.currentTimeMillis();
        String path = "/internal/v1/ai-results/ticket-analysis";
        assertThat(hmac.verify("ai-service", ts, "nonce", "POST", path, "{}", "0".repeat(64))).isFalse();
        verifyNoInteractions(store);
        String sig = HmacSignature.sign(AI_SECRET, ts, "nonce", "POST", path, "{}");
        when(store.claimNonce("ai-service:nonce")).thenReturn(true, false);
        assertThat(hmac.verify("ai-service", ts, "nonce", "POST", path, "{}", sig)).isTrue();
        assertThat(hmac.verify("ai-service", ts, "nonce", "POST", path, "{}", sig)).isFalse();
        assertThat(hmac.verify("aftersales-server", ts, "other", "POST", path, "{}", sig)).isFalse();
        assertThat(hmac.signature(ts, "nonce", "POST", path, "{}")).isEqualTo(HmacSignature.sign(JAVA_SECRET, ts, "nonce", "POST", path, "{}"));
    }

    @Test void internalFilterPreservesUtf8BodyAndRejectsUnsignedCall() throws Exception {
        var hmac = mock(InternalHmacService.class);
        var filter = new InternalHmacFilter(hmac);
        var req = new MockHttpServletRequest("POST", "/internal/v1/callback");
        byte[] body = "{\"text\":\"\u4e2d\u6587\"}".getBytes(StandardCharsets.UTF_8);
        req.setContent(body);
        var rejected = new MockHttpServletResponse();
        filter.doFilter(req, rejected, (r, s) -> fail("Should not reach controller"));
        assertThat(rejected.getStatus()).isEqualTo(401);
        when(hmac.verify(any(), anyLong(), any(), anyString(), anyString(), anyString(), any())).thenReturn(true);
        req.setContent(body);
        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> assertThat(r.getInputStream().readAllBytes()).containsExactly(body));
    }

    @Test void aiChatRequiresTicketAccessBeforeMakingNetworkCall() {
        var guard = mock(TicketAccessGuard.class);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(guard).requireAccess(any(), eq(10L));
        var controller = new AiChatProxyController(WebClient.builder(), new ObjectMapper(), "http://localhost:8000", JAVA_SECRET, guard);
        assertThatThrownBy(() -> controller.stream(user(3, UserRole.CUSTOMER), 10,
                new AiChatProxyController.ChatMessage("hello"))).isInstanceOf(ResponseStatusException.class);
    }

    @Test void productionRefusesDemoProfileAndPlaceholderSecrets() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "demo");
        assertThatThrownBy(() -> new ProductionSecurityValidator(env)).isInstanceOf(IllegalStateException.class);
        env.setActiveProfiles("prod");
        env.setProperty("app.security.jwt-secret", "replace-with-at-least-32-random-bytes");
        env.setProperty("app.ai.internal-secret", AI_SECRET);
        env.setProperty("app.ai.java-internal-secret", JAVA_SECRET);
        assertThatThrownBy(() -> new ProductionSecurityValidator(env)).isInstanceOf(IllegalStateException.class);
    }

    @Test void productionAcceptsExplicitCredentials() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("app.security.jwt-secret", "test-jwt-distinct-secret-with-32-bytes-1234");
        env.setProperty("app.ai.internal-secret", AI_SECRET);
        env.setProperty("app.ai.java-internal-secret", JAVA_SECRET);
        for (String key : new String[]{"spring.datasource.password", "spring.rabbitmq.password", "spring.data.redis.password", "app.storage.access-key", "app.storage.secret-key"}) {
            env.setProperty(key, "test-explicit-credential-1234");
        }
        assertThatCode(() -> new ProductionSecurityValidator(env)).doesNotThrowAnyException();
    }
}
