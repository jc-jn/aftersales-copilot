package com.aftersales.copilot;

import com.aftersales.copilot.aiadapter.security.InternalHmacFilter;
import com.aftersales.copilot.aiadapter.security.InternalHmacService;
import com.aftersales.copilot.auth.application.JwtService;
import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import com.aftersales.copilot.auth.domain.UserRole;
import com.aftersales.copilot.auth.infrastructure.JwtAuthenticationFilter;
import com.aftersales.copilot.auth.infrastructure.SecurityConfig;
import com.aftersales.copilot.auth.infrastructure.SecurityStateStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@ContextConfiguration(classes = {Day22HttpSecurityTest.ProbeController.class, SecurityConfig.class,
        JwtAuthenticationFilter.class, InternalHmacFilter.class})
@TestPropertySource(properties = "app.security.cors-allowed-origins=http://localhost:5173")
class Day22HttpSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwt;
    @MockitoBean SecurityStateStore state;
    @MockitoBean InternalHmacService hmac;

    private void token(String value, UserRole role) {
        when(jwt.parseAccessToken(value)).thenReturn(new AuthenticatedUser(1, "test", null, "test", role));
    }

    @Test void adminPathRejectsCustomerAndAgentButAllowsAdmin() throws Exception {
        token("customer", UserRole.CUSTOMER);
        token("agent", UserRole.AGENT);
        token("admin", UserRole.ADMIN);
        mvc.perform(get("/api/v1/admin/probe")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/probe").header("Authorization", "Bearer customer")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/probe").header("Authorization", "Bearer agent")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/probe").header("Authorization", "Bearer admin")).andExpect(status().isOk());
    }

    @Test void unknownPathsAreDeniedEvenForAdmin() throws Exception {
        token("admin", UserRole.ADMIN);
        mvc.perform(get("/unlisted").header("Authorization", "Bearer admin")).andExpect(status().isForbidden());
    }

    @Test void corsAllowsConfiguredOriginAndRejectsOtherOrigins() throws Exception {
        mvc.perform(options("/api/v1/admin/probe").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/v1/admin/probe").header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }

    @Test void internalHmacIsVerifiedBeforeSpringSecurityAndDoesNotRequireUserJwt() throws Exception {
        mvc.perform(post("/internal/v1/probe").content("{}")).andExpect(status().isUnauthorized());
        when(hmac.verify(eq("ai-service"), anyLong(), eq("nonce"), eq("POST"), eq("/internal/v1/probe"), eq("{}"), eq("signature"))).thenReturn(true);
        mvc.perform(post("/internal/v1/probe").content("{}").header("X-Internal-Service", "ai-service")
                .header("X-Internal-Timestamp", System.currentTimeMillis()).header("X-Internal-Nonce", "nonce")
                .header("X-Internal-Signature", "signature")).andExpect(status().isOk()).andExpect(content().string("{}"));
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/v1/admin/probe") String admin() { return "ok"; }
        @GetMapping("/unlisted") String unlisted() { return "ok"; }
        @PostMapping("/internal/v1/probe") String internal(@RequestBody String body) { return body; }
    }
}
