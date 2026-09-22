package com.aftersales.copilot.ticket.api;

import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import com.aftersales.copilot.common.api.ApiResponse;
import com.aftersales.copilot.ticket.application.TicketApplicationService;
import org.slf4j.MDC;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentWorkbenchController {
    private final TicketApplicationService service;
    public AgentWorkbenchController(TicketApplicationService service) { this.service = service; }
    @GetMapping("/workbench/summary") public ApiResponse<?> summary(@AuthenticationPrincipal AuthenticatedUser user) { return ok(service.workbench(user)); }
    @GetMapping("/tickets/queue") public ApiResponse<?> queue(@AuthenticationPrincipal AuthenticatedUser user,
                                                                  @RequestParam(defaultValue="1") int page,
                                                                  @RequestParam(defaultValue="20") int size) {
        return ok(service.list(user, Math.max(page, 1), Math.min(Math.max(size, 1), 100)));
    }
    private ApiResponse<?> ok(Object data) { return ApiResponse.success(data, MDC.get("traceId")); }
}
