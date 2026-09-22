package com.aftersales.copilot.ticket.api;
import com.aftersales.copilot.auth.domain.AuthenticatedUser; import com.aftersales.copilot.auth.domain.UserRole; import com.aftersales.copilot.common.api.ApiResponse; import com.aftersales.copilot.ticket.application.TicketApplicationService; import jakarta.validation.Valid; import org.slf4j.MDC; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/tickets") public class TicketController {
 private final TicketApplicationService service; public TicketController(TicketApplicationService service){this.service=service;}
 @PostMapping public ApiResponse<?> create(@AuthenticationPrincipal AuthenticatedUser u,@Valid @RequestBody TicketRequests.Create r){return ok(service.create(u,r));}
 @GetMapping public ApiResponse<?> list(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return ok(service.list(u,Math.max(1,page),Math.min(100,Math.max(1,size))));}
 @GetMapping("/{id}") public ApiResponse<?> detail(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id){return ok(service.detail(u,id));}
 @GetMapping("/{id}/timeline") public ApiResponse<?> timeline(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id){return ok(service.timeline(u,id));}
 @PostMapping("/{id}/messages") public ApiResponse<?> message(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id,@Valid @RequestBody TicketRequests.Message r){return ok(service.message(u,id,r));}
 @PostMapping("/{id}/supplement") public ApiResponse<?> supplement(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id,@Valid @RequestBody TicketRequests.Command r){return ok(service.supplement(u,id,r.version()));}
 @PostMapping("/{id}/cancel") public ApiResponse<?> cancel(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id,@Valid @RequestBody TicketRequests.Command r){return ok(service.transition(u,id,"CANCELLED",r.version(),"CANCEL"));}
 @PostMapping("/{id}/close") public ApiResponse<?> close(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id,@Valid @RequestBody TicketRequests.Command r){return ok(service.transition(u,id,"CLOSED",r.version(),"CLOSE"));}
 @PostMapping("/{id}/request-info") public ApiResponse<?> requestInfo(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id,@Valid @RequestBody TicketRequests.Command r){return ok(service.transition(u,id,"PENDING_CUSTOMER",r.version(),"REQUEST_INFO"));}
 @PostMapping("/{id}/claim") public ApiResponse<?> claim(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id,@Valid @RequestBody TicketRequests.Command r){return ok(service.claim(u,id,r.version()));}
 @PostMapping("/{id}/reject") public ApiResponse<?> reject(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long id,@Valid @RequestBody TicketRequests.Command r){return ok(service.transition(u,id,"REJECTED",r.version(),"REJECT"));}
 private <T> ApiResponse<T> ok(T v){return ApiResponse.success(v,MDC.get("traceId"));}
}
