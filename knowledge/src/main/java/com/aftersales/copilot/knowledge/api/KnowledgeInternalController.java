package com.aftersales.copilot.knowledge.api;

import com.aftersales.copilot.knowledge.application.KnowledgeDocumentService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/knowledge/documents")
public class KnowledgeInternalController {
    private final KnowledgeDocumentService service;
    public KnowledgeInternalController(KnowledgeDocumentService service) { this.service = service; }

    @PostMapping("/{id}/download")
    public Map<String, Object> download(@PathVariable long id) { return service.indexingDownload(id); }
}
