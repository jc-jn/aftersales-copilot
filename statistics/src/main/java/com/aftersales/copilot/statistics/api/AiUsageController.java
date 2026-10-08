package com.aftersales.copilot.statistics.api;

import com.aftersales.copilot.statistics.application.AiCallService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/ai-results")
public class AiUsageController {
    private final AiCallService calls;
    public AiUsageController(AiCallService calls) { this.calls = calls; }

    @PostMapping("/chat-usage")
    public Map<String, Object> chat(@RequestBody ChatUsage body) {
        boolean changed = calls.finish(body.callId(), null, body.ticketId(), "CHAT", body.status(),
                body.usage() == null ? Map.of() : body.usage(), body.errorCode());
        return Map.of("accepted", true, "duplicate", !changed);
    }
    public record ChatUsage(String callId, long ticketId, String status, Map<String, Object> usage, String errorCode) {}
}
