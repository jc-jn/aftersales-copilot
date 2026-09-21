package com.aftersales.copilot;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/api/v1/ping")
    public Map<String, String> ping() {
        return Map.of("service", "aftersales-server", "status", "ok");
    }
}
