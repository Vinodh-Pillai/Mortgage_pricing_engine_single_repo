package com.wcpe.auditreplay.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class AuditProblemDetailsAdvice {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<AuditProblemDetail> responseStatus(ResponseStatusException ex, HttpServletRequest request) {
        String detail = ex.getReason() == null ? "Request failed" : ex.getReason();
        return problem(ex.getStatusCode(), detail, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<AuditProblemDetail> badRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    private ResponseEntity<AuditProblemDetail> problem(HttpStatusCode status, String detail, HttpServletRequest request) {
        AuditProblemDetail problem = new AuditProblemDetail(
                "about:blank", title(status), status.value(), detail, code(status), request.getRequestURI());
        return ResponseEntity.status(status).body(problem);
    }

    public record AuditProblemDetail(String type, String title, int status, String detail, String code, String path) {
    }

    private static String title(HttpStatusCode status) {
        if (status.is4xxClientError()) {
            return "Audit request rejected";
        }
        return "Audit service error";
    }

    private static String code(HttpStatusCode status) {
        if (status.value() == HttpStatus.UNAUTHORIZED.value()) {
            return "UNAUTHENTICATED";
        }
        if (status.value() == HttpStatus.FORBIDDEN.value()) {
            return "TENANT_ACCESS_DENIED";
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return "NOT_FOUND";
        }
        if (status.value() == HttpStatus.CONFLICT.value()) {
            return "IDEMPOTENCY_CONFLICT";
        }
        return "VALIDATION_FAILED";
    }
}
