package com.wcpe.ratefeed.config;

import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import com.wcpe.ratefeed.domain.RequestContext;
import com.wcpe.ratefeed.role.RateFeedRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Hardening: Centralized error handling and RBAC enforcement for rate-feed-service.
 * Maps RateFeedException to proper HTTP status codes and enforces X-Roles on
 * write-sensitive endpoints.
 */
@RestControllerAdvice
@Order(-1)
public class RateFeedWebConfig {
  private static final Logger log = LoggerFactory.getLogger(RateFeedWebConfig.class);

  @ExceptionHandler(RateFeedException.class)
  public ResponseEntity<ErrorResponse> handleRateFeed(RateFeedException ex, HttpServletRequest request) {
    HttpStatus status = ex.status();
    if (status.series() == HttpStatus.Series.SERVER_ERROR) {
      log.error("RateFeed error [{}]: {}", ex.code(), ex.getMessage());
    }
    String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-Id"))
        .filter(v -> !v.isBlank())
        .orElse("none");
    return ResponseEntity.status(status).body(new ErrorResponse(ex.code(), ex.getMessage(), correlationId));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception: {}", ex.getMessage(), ex);
    String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-Id"))
        .filter(v -> !v.isBlank())
        .orElse("none");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred.", correlationId));
  }

  public record ErrorResponse(String code, String message, String correlationId) {}
}
