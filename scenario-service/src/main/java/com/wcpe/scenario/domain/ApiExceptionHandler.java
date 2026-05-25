package com.wcpe.scenario.domain;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(ScenarioException.class)
  ResponseEntity<Map<String, Object>> scenario(ScenarioException ex) {
    return ResponseEntity.status(ex.status()).body(Map.of("code", ex.code(), "message", ex.getMessage(), "fieldErrors", ex.fieldErrors()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, Object>> generic(Exception ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("code", "VALIDATION_FAILED", "message", ex.getMessage()));
  }
}
