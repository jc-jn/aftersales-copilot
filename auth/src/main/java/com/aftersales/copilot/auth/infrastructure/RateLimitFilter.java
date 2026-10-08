package com.aftersales.copilot.auth.infrastructure;

import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import com.aftersales.copilot.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;

public class RateLimitFilter extends OncePerRequestFilter {
    private final SecurityStateStore store;
    private final ObjectMapper mapper;
    private final int authLimit, aiLimit, uploadLimit, confirmLimit;

    public RateLimitFilter(SecurityStateStore store, ObjectMapper mapper, int authLimit, int aiLimit, int uploadLimit, int confirmLimit) {
        this.store = store;
        this.mapper = mapper;
        this.authLimit = authLimit;
        this.aiLimit = aiLimit;
        this.uploadLimit = uploadLimit;
        this.confirmLimit = confirmLimit;
        if (Math.min(Math.min(authLimit, aiLimit), Math.min(uploadLimit, confirmLimit)) <= 0) {
            throw new IllegalArgumentException("Rate limits must be positive");
        }
    }

    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getServletPath();
        String category = null;
        int limit = 0;
        if (req.getMethod().equals("POST")) {
            if (path.matches("/api/v1/auth/(login|refresh|logout)")) { category = "auth"; limit = authLimit; }
            else if (path.matches("/api/v1/tickets/[^/]+/(ai-chat/stream|ai-analysis(?:/retry)?)")) { category = "ai"; limit = aiLimit; }
            else if (path.matches("/api/v1/tickets/[^/]+/attachments") || path.equals("/api/v1/admin/knowledge/documents")) { category = "upload"; limit = uploadLimit; }
            else if (path.matches("/api/v1/proposals/[^/]+/confirm")) { category = "confirm"; limit = confirmLimit; }
        }
        if (category != null) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            String identity = auth != null && auth.getPrincipal() instanceof AuthenticatedUser user && !category.equals("auth")
                    ? "user:" + user.id() : "ip:" + req.getRemoteAddr();
            long window = System.currentTimeMillis() / 60000;
            try {
                if (store.increment(category + ":" + identity + ":" + window, Duration.ofSeconds(60)) > limit) {
                    res.setHeader("Retry-After", Long.toString(60 - System.currentTimeMillis() / 1000 % 60));
                    error(res, 429, "RATE_LIMITED");
                    return;
                }
            } catch (ResponseStatusException e) {
                error(res, 503, "SECURITY_STATE_UNAVAILABLE");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private void error(HttpServletResponse res, int status, String code) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getWriter(), ApiResponse.failure(code, code, null, MDC.get("traceId")));
    }
}
