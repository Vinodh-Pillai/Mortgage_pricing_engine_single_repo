package com.wcpe.lock;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class LockApiExceptionHandler {
  @ExceptionHandler(LockServiceException.class)
  ResponseEntity<ErrorResponse> lockServiceException(LockServiceException error) {
    return ResponseEntity.status(statusFor(error.code())).body(new ErrorResponse(error.code(), error.getMessage()));
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ErrorResponse> missingHeader(MissingRequestHeaderException error) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("VALIDATION_FAILED", error.getMessage()));
  }

  private HttpStatus statusFor(String code) {
    if ("TENANT_ACCESS_DENIED".equals(code)) {
      return HttpStatus.FORBIDDEN;
    }
    if ("NOT_FOUND".equals(code)) {
      return HttpStatus.NOT_FOUND;
    }
    if ("PERSISTENCE_NOT_DURABLE".equals(code)) {
      return HttpStatus.SERVICE_UNAVAILABLE;
    }
    if (code != null && (code.contains("CONFLICT") || code.startsWith("DUPLICATE") || code.startsWith("OPEN_"))) {
      return HttpStatus.CONFLICT;
    }
    if (code != null && (code.contains("POLICY") || code.contains("CONFIG") || code.contains("ELIGIBILITY"))) {
      return HttpStatus.UNPROCESSABLE_ENTITY;
    }
    return HttpStatus.BAD_REQUEST;
  }

  public record ErrorResponse(String code, String message) {}
}
