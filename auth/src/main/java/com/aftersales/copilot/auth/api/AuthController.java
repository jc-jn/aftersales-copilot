package com.aftersales.copilot.auth.api;

import com.aftersales.copilot.auth.application.AuthApplicationService;
import com.aftersales.copilot.auth.application.TokenPair;
import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import com.aftersales.copilot.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthApplicationService authService;

    public AuthController(AuthApplicationService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<TokenPair> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success(authService.login(request.username(), request.password(), servletRequest.getHeader("User-Agent")), traceId());
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenPair> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success(authService.refresh(request.refreshToken(), servletRequest.getHeader("User-Agent")), traceId());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.success(null, traceId());
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(UserResponse.from(user), traceId());
    }

    private static String traceId() {
        return MDC.get("traceId");
    }
}
