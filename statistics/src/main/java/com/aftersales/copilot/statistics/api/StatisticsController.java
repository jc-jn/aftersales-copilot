package com.aftersales.copilot.statistics.api;

import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import com.aftersales.copilot.auth.domain.UserRole;
import com.aftersales.copilot.common.api.ApiResponse;
import com.aftersales.copilot.statistics.application.StatisticsService;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin")
public class StatisticsController {
    private final StatisticsService service;
    public StatisticsController(StatisticsService service) { this.service = service; }

    @GetMapping("/dashboard/overview")
    public ApiResponse<?> overview(@AuthenticationPrincipal AuthenticatedUser user,
                                  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        admin(user);
        return ApiResponse.success(service.overview(StatisticsService.range(from, to)), MDC.get("traceId"));
    }

    @GetMapping("/statistics/ai-usage")
    public ApiResponse<?> usage(@AuthenticationPrincipal AuthenticatedUser user,
                               @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(defaultValue="day") String groupBy) {
        admin(user);
        if (!groupBy.equals("day")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "STATISTICS_GROUP_INVALID");
        return ApiResponse.success(service.usage(StatisticsService.range(from, to)), MDC.get("traceId"));
    }

    private void admin(AuthenticatedUser user) {
        if (user == null || user.role() != UserRole.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED");
    }
}
