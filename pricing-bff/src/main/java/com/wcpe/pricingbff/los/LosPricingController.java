package com.wcpe.pricingbff.los;

import com.wcpe.pricingbff.los.LosApiModels.ErrorResponse;
import com.wcpe.pricingbff.los.LosApiModels.LosLockExtendRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosLockRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosPricingRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosWebhookRegistrationRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
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
@RequestMapping("/api/v1/los")
class LosPricingController {
  private final LosPricingService service;

  LosPricingController(LosPricingService service) {
    this.service = service;
  }

  @PostMapping("/pricing-requests")
  ResponseEntity<?> createPricingRequest(@RequestBody LosPricingRequest request,
      @RequestHeader("X-Request-ID") String idempotencyKey,
      @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.createPricingRequest(request, idempotencyKey, correlationId));
  }

  @GetMapping("/pricing-requests/{id}")
  Object getPricingRequest(@PathVariable String id) {
    return service.getPricingRequest(id);
  }

  @GetMapping("/pricing-requests/{id}/offers")
  Object getOffers(@PathVariable String id) {
    return Map.of("pricingRequestId", id, "offers", service.getOffers(id));
  }

  @PostMapping("/locks")
  ResponseEntity<?> requestLock(@RequestBody LosLockRequest request,
      @RequestHeader("X-Request-ID") String idempotencyKey,
      @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.requestLock(request, idempotencyKey, correlationId));
  }

  @GetMapping("/locks/{id}")
  Object getLock(@PathVariable String id) {
    return service.getLock(id);
  }

  @PostMapping("/locks/{id}/extend")
  Object extendLock(@PathVariable String id,
      @RequestBody LosLockExtendRequest request,
      @RequestHeader("X-Request-ID") String idempotencyKey,
      @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
    return service.extendLock(id, request, idempotencyKey, correlationId);
  }

  @PostMapping("/webhooks")
  ResponseEntity<?> registerWebhook(@RequestBody LosWebhookRegistrationRequest request,
      @RequestHeader(value = "X-Tenant-ID", required = false, defaultValue = "unknown") String tenantId) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.registerWebhook(request, tenantId));
  }

  @ExceptionHandler(LosValidationException.class)
  ResponseEntity<ErrorResponse> validationError(LosValidationException ex, HttpServletRequest request) {
    HttpStatus status = ex.code().endsWith("NOT_FOUND") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
    return ResponseEntity.status(status).body(new ErrorResponse(ex.code(), ex.getMessage(), request.getHeader("X-Correlation-ID")));
  }
}
