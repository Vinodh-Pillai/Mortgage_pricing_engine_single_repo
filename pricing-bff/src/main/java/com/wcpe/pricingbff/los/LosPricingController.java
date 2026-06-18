package com.wcpe.pricingbff.los;

import com.wcpe.pricingbff.los.LosApiModels.ErrorResponse;
import com.wcpe.pricingbff.los.LosApiModels.LosLockExtendRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosLockRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosProductCatalogResponse;
import com.wcpe.pricingbff.los.LosApiModels.LosProductDetailResponse;
import com.wcpe.pricingbff.los.LosApiModels.LosProductEligibilityRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
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
      @RequestHeader(value = "Idempotency-Key", required = false) String loanPassIdempotencyKey,
      @RequestHeader(value = "X-Request-ID", required = false) String requestId,
      @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
    String idempotencyKey = loanPassIdempotencyKey == null || loanPassIdempotencyKey.isBlank()
        ? requestId : loanPassIdempotencyKey;
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

  @GetMapping("/products")
  LosProductCatalogResponse getProducts(
      @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
      @RequestParam(required = false) String channel,
      @RequestParam(required = false) String investor,
      @RequestParam(required = false) String productFamily,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) String effectiveDate,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return service.getProductCatalog(tenantId, channel, investor, productFamily, active, effectiveDate, page, pageSize);
  }

  @GetMapping("/products/search")
  LosProductCatalogResponse searchProducts(
      @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
      @RequestParam Map<String, String> queryParameters) {
    return service.searchProductCatalog(tenantId, queryParameters);
  }

  @GetMapping("/products/{productId}")
  LosProductDetailResponse getProductDetail(
      @PathVariable String productId,
      @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId) {
    return service.getProductDetail(productId, tenantId);
  }

  @PostMapping("/product-eligibility")
  Object evaluateProductEligibility(@RequestBody LosProductEligibilityRequest request,
      @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
      @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
    return service.evaluateProductEligibility(request, tenantId, correlationId);
  }

  @PostMapping("/locks")
  ResponseEntity<?> requestLock(@RequestBody LosLockRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String loanPassIdempotencyKey,
      @RequestHeader(value = "X-Request-ID", required = false) String requestId,
      @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
    String idempotencyKey = idempotencyKey(loanPassIdempotencyKey, requestId);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.requestLock(request, idempotencyKey, correlationId));
  }

  @GetMapping("/locks/{id}")
  Object getLock(@PathVariable String id) {
    return service.getLock(id);
  }

  @PostMapping("/locks/{id}/extend")
  Object extendLock(@PathVariable String id,
      @RequestBody LosLockExtendRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String loanPassIdempotencyKey,
      @RequestHeader(value = "X-Request-ID", required = false) String requestId,
      @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
    String idempotencyKey = idempotencyKey(loanPassIdempotencyKey, requestId);
    return service.extendLock(id, request, idempotencyKey, correlationId);
  }

  @PostMapping("/webhooks")
  ResponseEntity<?> registerWebhook(@RequestBody LosWebhookRegistrationRequest request,
      @RequestHeader(value = "X-Tenant-ID", required = false, defaultValue = "unknown") String tenantId) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.registerWebhook(request, tenantId));
  }

  @ExceptionHandler(LosValidationException.class)
  ResponseEntity<ErrorResponse> validationError(LosValidationException ex, HttpServletRequest request) {
    HttpStatus status = ex.code().endsWith("NOT_FOUND") ? HttpStatus.NOT_FOUND
        : ex.code().endsWith("DISABLED") ? HttpStatus.FORBIDDEN
        : HttpStatus.BAD_REQUEST;
    return ResponseEntity.status(status).body(new ErrorResponse(ex.code(), ex.getMessage(), request.getHeader("X-Correlation-ID")));
  }

  private String idempotencyKey(String loanPassIdempotencyKey, String requestId) {
    return loanPassIdempotencyKey == null || loanPassIdempotencyKey.isBlank() ? requestId : loanPassIdempotencyKey;
  }
}
