package com.wcpe.quote.los;

import com.wcpe.quote.los.LosQuoteModels.LosQuoteError;
import com.wcpe.quote.los.LosQuoteModels.LosQuoteRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/los/quote-requests")
class LosQuoteIntegrationController {
  private final LosQuoteIntegrationService service;

  LosQuoteIntegrationController(LosQuoteIntegrationService service) {
    this.service = service;
  }

  @PostMapping
  ResponseEntity<?> startLosQuote(@RequestBody LosQuoteRequest request,
      @RequestHeader(value = "X-Request-ID", required = false) String requestId,
      @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.start(request, requestId, correlationId));
  }

  @GetMapping("/{id}")
  ResponseEntity<?> getLosQuote(@PathVariable String id) {
    return service.get(id)
        .<ResponseEntity<?>>map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new LosQuoteError("LOS_QUOTE_JOB_NOT_FOUND", "LOS quote job not found", null)));
  }

  @ExceptionHandler(LosQuoteValidationException.class)
  ResponseEntity<LosQuoteError> validationError(LosQuoteValidationException ex, HttpServletRequest request) {
    return ResponseEntity.badRequest().body(new LosQuoteError(ex.code(), ex.getMessage(), request.getHeader("X-Correlation-ID")));
  }
}
