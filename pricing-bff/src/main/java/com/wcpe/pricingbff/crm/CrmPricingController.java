package com.wcpe.pricingbff.crm;

import com.wcpe.pricingbff.crm.CrmApiModels.CrmWebhookRegistrationRequest;
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
@RequestMapping("/api/v1/tenants/{tenantId}/integrations/crm")
class CrmPricingController {
  private final CrmPricingService service;

  CrmPricingController(CrmPricingService service) {
    this.service = service;
  }

  @PostMapping("/pricing-requests")
  ResponseEntity<?> createPricingRequest(@PathVariable String tenantId,
      @RequestBody(required = false) Map<String, Object> payload,
      @RequestHeader("X-Request-ID") String idempotencyKey,
      @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
      HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.createPricingRequest(tenantId, crmSystem(request), payload, idempotencyKey, correlationId));
  }

  @PostMapping("/{sourceSystem}/pricing-requests")
  ResponseEntity<?> createSourcePricingRequest(@PathVariable String tenantId,
      @PathVariable String sourceSystem,
      @RequestBody(required = false) Map<String, Object> payload,
      @RequestHeader("X-Request-ID") String idempotencyKey,
      @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.createPricingRequest(tenantId, normalizeSourceSystem(sourceSystem), payload, idempotencyKey, correlationId));
  }

  @GetMapping("/pricing-requests/{requestId}")
  Object getPricingRequest(@PathVariable String tenantId, @PathVariable String requestId) {
    return service.getPricingRequest(tenantId, requestId);
  }

  @PostMapping("/pricing-requests/{requestId}/continue")
  Object continueRequest(@PathVariable String tenantId, @PathVariable String requestId,
      @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
    return service.continueRequest(tenantId, requestId, correlationId);
  }

  @PostMapping("/pricing-requests/{requestId}/push")
  Object pushPricingUpdate(@PathVariable String tenantId, @PathVariable String requestId,
      @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
      HttpServletRequest request) {
    return Map.of("requestId", requestId, "deliveries", service.pushPricingUpdate(tenantId, crmSystem(request), requestId, correlationId));
  }

  @GetMapping("/dashboard")
  Object dashboard(@PathVariable String tenantId, HttpServletRequest request) {
    return service.dashboard(tenantId, crmSystem(request));
  }

  @PostMapping("/pricing-requests/{requestId}/scenarios")
  Object saveScenario(@PathVariable String tenantId, @PathVariable String requestId) {
    return service.saveScenario(tenantId, requestId);
  }

  @PostMapping("/scenarios/{scenarioId}/share")
  Object shareScenario(@PathVariable String tenantId, @PathVariable String scenarioId,
      @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
    return service.shareScenario(tenantId, scenarioId, correlationId);
  }

  @PostMapping("/webhooks")
  ResponseEntity<?> registerWebhook(@PathVariable String tenantId,
      @RequestBody CrmWebhookRegistrationRequest request,
      HttpServletRequest servletRequest) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.registerWebhook(tenantId, crmSystem(servletRequest), request));
  }

  @GetMapping("/webhooks/deliveries")
  Object webhookDeliveries() {
    return Map.of("deliveries", service.deliveries());
  }

  @ExceptionHandler(CrmValidationException.class)
  ResponseEntity<ErrorBody> validationError(CrmValidationException ex, HttpServletRequest request) {
    HttpStatus status = ex.code().endsWith("NOT_FOUND") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
    return ResponseEntity.status(status).body(new ErrorBody(ex.code(), ex.getMessage(), request.getHeader("X-Correlation-ID")));
  }

  private String crmSystem(HttpServletRequest request) {
    Object value = request.getAttribute("crm.system");
    return value == null ? "SALESFORCE" : String.valueOf(value);
  }

  private String normalizeSourceSystem(String sourceSystem) {
    String normalized = sourceSystem == null ? "" : sourceSystem.toUpperCase();
    if (!"SALESFORCE".equals(normalized) && !"HUBSPOT".equals(normalized)) {
      throw new CrmValidationException("CRM_SYSTEM_REQUIRED", "CRM path must identify SALESFORCE or HUBSPOT");
    }
    return normalized;
  }

  private record ErrorBody(String code, String message, String correlationId) {
  }
}
