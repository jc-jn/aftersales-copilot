package com.aftersales.copilot.aiadapter.api;

import com.aftersales.copilot.auth.application.TicketAccessGuard;
import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import com.aftersales.copilot.common.observability.TraceIds;
import com.aftersales.copilot.common.security.HmacSignature;
import com.aftersales.copilot.statistics.application.AiCallService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/v1/tickets")
public class AiChatProxyController {
    private static final Logger log = LoggerFactory.getLogger(AiChatProxyController.class);
    private final WebClient client;
    private final ObjectMapper mapper;
    private final String secret;
    private final TicketAccessGuard access;
    private final AiCallService calls;

    public AiChatProxyController(WebClient.Builder builder, ObjectMapper mapper, @Value("${app.ai.base-url}") String base,
                                 @Value("${app.ai.java-internal-secret}") String secret, TicketAccessGuard access, AiCallService calls) {
        this.client = builder.baseUrl(base).build(); this.mapper = mapper; this.secret = secret; this.access = access; this.calls = calls;
    }

    @PostMapping(value="/{ticketId}/ai-chat/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long ticketId,
                                                @Valid @RequestBody ChatMessage body) {
        access.requireAccess(user, ticketId);
        String traceId = TraceIds.normalize(MDC.get("traceId")), callId = UUID.randomUUID().toString();
        calls.begin(callId, null, ticketId, "CHAT", traceId);
        AtomicBoolean ended = new AtomicBoolean();
        long start = System.nanoTime();
        return Flux.defer(() -> {
            try {
                String json = mapper.writeValueAsString(Map.of("ticketId", ticketId, "message", body.message(), "callId", callId));
                String nonce = UUID.randomUUID().toString(); long timestamp = System.currentTimeMillis();
                return client.post().uri("/internal/v1/chat/stream").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId).header("X-Internal-Service", "aftersales-server")
                        .header("X-Internal-Timestamp", Long.toString(timestamp)).header("X-Internal-Nonce", nonce)
                        .header("X-Internal-Signature", HmacSignature.sign(secret, timestamp, nonce, "POST", "/internal/v1/chat/stream", json))
                        .bodyValue(json).retrieve().bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                        .timeout(java.time.Duration.ofSeconds(60));
            } catch (Exception e) { return Flux.error(e); }
        }).doOnNext(event -> {
            if ("done".equals(event.event()) || "error".equals(event.event())) ended.set(true);
        }).onErrorResume(error -> Mono.fromRunnable(() -> calls.failPending(callId, "FAILED", "AI_CHAT_TRANSPORT_FAILED"))
                .subscribeOn(Schedulers.boundedElastic()).thenReturn(ServerSentEvent.<String>builder()
                        .event("error").data("{\"code\":\"AI_CHAT_UNAVAILABLE\",\"retryable\":true}").build()))
          .doFinally(signal -> {
              String terminal = signal == SignalType.CANCEL ? "INTERRUPTED" : "FAILED";
              // A confirmed usage callback already finalized the row. Only unresolved requests are changed here.
              Mono.fromRunnable(() -> calls.failPending(callId, terminal, ended.get() ? "AI_USAGE_NOT_RECORDED" : "AI_STREAM_INCOMPLETE"))
                      .subscribeOn(Schedulers.boundedElastic()).subscribe(ignored -> {}, error ->
                              log.atError().addKeyValue("event", "ai_usage_write_failed").addKeyValue("traceId", traceId).log("AI usage persistence failed"));
              log.atInfo().addKeyValue("event", "sse_completed").addKeyValue("traceId", traceId)
                      .addKeyValue("callId", callId).addKeyValue("termination", signal.name())
                      .addKeyValue("durationMs", (System.nanoTime() - start) / 1_000_000).log("AI stream completed");
          });
    }

    public record ChatMessage(@NotBlank @Size(max=4000) String message) {}
}
