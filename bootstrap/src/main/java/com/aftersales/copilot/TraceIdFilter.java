package com.aftersales.copilot;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import com.aftersales.copilot.common.observability.TraceIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = TraceIds.normalize(request.getHeader("X-Trace-Id"));
        String previous = MDC.get("traceId");
        long start = System.nanoTime();
        MDC.put("traceId", traceId);
        response.setHeader("X-Trace-Id", traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            log.atInfo().addKeyValue("event", "http_request").addKeyValue("method", request.getMethod())
                    .addKeyValue("route", route == null ? "UNMATCHED" : route.toString())
                    .addKeyValue("status", response.getStatus()).addKeyValue("async", request.isAsyncStarted())
                    .addKeyValue("durationMs", (System.nanoTime() - start) / 1_000_000).log("HTTP request handled");
            if (previous == null) MDC.remove("traceId"); else MDC.put("traceId", previous);
        }
    }
}
