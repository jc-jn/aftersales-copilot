package com.aftersales.copilot.aiadapter.api;

import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import com.aftersales.copilot.auth.domain.UserRole;
import com.aftersales.copilot.common.ai.AiTaskRetryService;
import com.aftersales.copilot.common.api.ApiResponse;
import org.slf4j.MDC;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tickets")
public class AiAnalysisController {
    private final AiTaskRetryService service;
    public AiAnalysisController(AiTaskRetryService service) { this.service = service; }
    @GetMapping("/{ticketId}/ai-analysis/latest")
    public ApiResponse<?> latest(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long ticketId) { check(user); return ok(service.latestAnalysis(ticketId)); }
    @PostMapping("/{ticketId}/ai-analysis/retry")
    public ApiResponse<?> retry(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long ticketId) { check(user); return ok(service.retryTicketAnalysis(ticketId, user.id())); }
    private void check(AuthenticatedUser user) { if (user == null || (user.role() != UserRole.AGENT && user.role() != UserRole.ADMIN)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN); }
    private ApiResponse<?> ok(Object value) { return ApiResponse.success(value, MDC.get("traceId")); }
}
