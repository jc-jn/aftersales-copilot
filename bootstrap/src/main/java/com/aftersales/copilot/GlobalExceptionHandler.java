package com.aftersales.copilot;

import com.aftersales.copilot.auth.application.AuthException;
import com.aftersales.copilot.common.api.ApiResponse;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.aftersales.copilot.ticket.domain.TicketException;
import com.aftersales.copilot.proposal.domain.ProposalException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<ApiResponse<Void>> handleStatus(org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(ApiResponse.failure(
                exception.getReason() == null ? "REQUEST_REJECTED" : exception.getReason(), "请求被拒绝", null, MDC.get("traceId")));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> handleUploadSize(Exception exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiResponse.failure("FILE_SIZE_INVALID", "文件超出大小限制", null, MDC.get("traceId")));
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiResponse<Void>> handleMalformed(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("VALIDATION_ERROR", "请求参数不合法", null, MDC.get("traceId")));
    }

    @ExceptionHandler(AuthException.class)
    ResponseEntity<ApiResponse<Void>> handleAuth(AuthException exception) {
        return ResponseEntity.status(exception.status())
                .body(ApiResponse.failure(exception.code(), exception.getMessage(), null, MDC.get("traceId")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        var field = exception.getBindingResult().getFieldError();
        Object details = field == null ? null : Map.of("field", field.getField(), "reason", field.getDefaultMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure("VALIDATION_ERROR", "请求参数不合法", details, MDC.get("traceId")));
    }

    @ExceptionHandler(TicketException.class)
    ResponseEntity<ApiResponse<Void>> handleTicket(TicketException exception) {
        return ResponseEntity.status(exception.status()).body(ApiResponse.failure(exception.code(), exception.getMessage(), exception.details(), MDC.get("traceId")));
    }

    @ExceptionHandler(ProposalException.class)
    ResponseEntity<ApiResponse<Void>> handleProposal(ProposalException exception) {
        return ResponseEntity.status(exception.status()).body(ApiResponse.failure(exception.code(), exception.getMessage(), exception.details(), MDC.get("traceId")));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.atError().addKeyValue("event", "request_failed").addKeyValue("errorType", exception.getClass().getSimpleName())
                .log("Unhandled request error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("INTERNAL_ERROR", "服务器内部错误", null, MDC.get("traceId")));
    }
}
