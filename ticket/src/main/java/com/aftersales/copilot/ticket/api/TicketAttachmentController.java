package com.aftersales.copilot.ticket.api;

import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import com.aftersales.copilot.common.api.ApiResponse;
import com.aftersales.copilot.ticket.application.TicketAttachmentService;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/attachments")
public class TicketAttachmentController {
    private final TicketAttachmentService service;
    public TicketAttachmentController(TicketAttachmentService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> upload(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long ticketId, @RequestPart MultipartFile file) {
        return ApiResponse.success(service.upload(user, ticketId, file), MDC.get("traceId"));
    }

    @GetMapping
    public ApiResponse<?> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long ticketId) {
        return ApiResponse.success(service.list(user, ticketId), MDC.get("traceId"));
    }

    @GetMapping("/{attachmentId}/download")
    public ApiResponse<?> download(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long ticketId, @PathVariable long attachmentId) {
        return ApiResponse.success(service.download(user, ticketId, attachmentId), MDC.get("traceId"));
    }
}
