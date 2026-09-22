package com.aftersales.copilot.proposal.api;

import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import com.aftersales.copilot.common.api.ApiResponse;
import com.aftersales.copilot.proposal.application.ProposalApplicationService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ProposalController {
    private final ProposalApplicationService service;
    public ProposalController(ProposalApplicationService service) { this.service = service; }
    @PostMapping("/tickets/{ticketId}/proposals") public ApiResponse<?> create(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long ticketId,@Valid @RequestBody ProposalRequests.Create r){return ok(service.create(u,ticketId,r));}
    @GetMapping("/tickets/{ticketId}/proposals") public ApiResponse<?> list(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long ticketId){return ok(service.list(u,ticketId));}
    @PostMapping("/proposals/{proposalId}/publish") public ApiResponse<?> publish(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long proposalId,@Valid @RequestBody ProposalRequests.Version r){return ok(service.publish(u,proposalId,r));}
    @PostMapping("/proposals/{proposalId}/reject") public ApiResponse<?> reject(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long proposalId,@Valid @RequestBody ProposalRequests.Version r){return ok(service.reject(u,proposalId,r));}
    @PostMapping("/proposals/{proposalId}/confirm") public ApiResponse<?> confirm(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long proposalId,@Valid @RequestBody ProposalRequests.Confirm r,@RequestHeader(value="Idempotency-Key",required=false) String key){return ok(service.confirm(u,proposalId,r,key));}
    @PostMapping("/return-orders/{returnId}/shipment") public ApiResponse<?> shipment(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long returnId,@Valid @RequestBody ProposalRequests.Shipment r){return ok(service.shipReturn(u,returnId,r));}
    @PostMapping("/return-orders/{returnId}/receive") public ApiResponse<?> receive(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long returnId,@Valid @RequestBody ProposalRequests.Receive r){return ok(service.receiveReturn(u,returnId,r));}
    @PostMapping("/return-orders/{returnId}/inspect") public ApiResponse<?> inspect(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable long returnId,@Valid @RequestBody ProposalRequests.Inspect r){return ok(service.inspectReturn(u,returnId,r));}
    private ApiResponse<?> ok(Object value){return ApiResponse.success(value,MDC.get("traceId"));}
}
