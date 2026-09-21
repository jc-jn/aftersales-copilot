package com.aftersales.copilot.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String code, String message, T data, Object details, String traceId) {
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("OK", "success", data, null, traceId);
    }

    public static ApiResponse<Void> failure(String code, String message, Object details, String traceId) {
        return new ApiResponse<>(code, message, null, details, traceId);
    }
}
