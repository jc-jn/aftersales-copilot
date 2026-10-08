package com.aftersales.copilot.knowledge.api;

import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import com.aftersales.copilot.knowledge.application.KnowledgeDocumentService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/knowledge/documents")
public class KnowledgeDocumentController {
    private final KnowledgeDocumentService service;

    public KnowledgeDocumentController(KnowledgeDocumentService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@AuthenticationPrincipal AuthenticatedUser user, @RequestPart MultipartFile file,
                                      @RequestParam String title, @RequestParam(defaultValue = "POLICY") String documentType,
                                      @RequestParam(defaultValue = "GLOBAL") String scopeType, @RequestParam(required = false) Long scopeId,
                                      @RequestParam(defaultValue = "v1") String versionLabel) {
        return service.upload(user, file, title, documentType, scopeType, scopeId, versionLabel);
    }

    @GetMapping("/{id}/download")
    public Map<String, Object> download(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return service.download(user, id);
    }
}
