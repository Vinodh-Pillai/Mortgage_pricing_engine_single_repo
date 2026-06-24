package com.wcpe.pricingbff.ui;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OfferUiController {
  private final PricingBffUiFallbackAdapter adapter;

  OfferUiController(PricingBffUiFallbackAdapter adapter) {
    this.adapter = adapter;
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/offers")
  Object offerComparison(@PathVariable String tenantId, @PathVariable String runId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.offerComparison(tenantId, runId, uiTraceId);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/offers/{offerId}/explain")
  Object offerExplanation(@PathVariable String runId, @PathVariable String offerId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.offerExplanation(runId, offerId, uiTraceId);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/offers/{offerId}/detail")
  Object quoteDetail(@PathVariable String tenantId, @PathVariable String runId, @PathVariable String offerId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.quoteDetail(tenantId, runId, offerId, uiTraceId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/offers/{offerId}/select")
  ResponseEntity<?> selectOffer(@PathVariable String runId, @PathVariable String offerId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.selectOffer(runId, offerId, uiTraceId);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/lock")
  Object lockWorkflow(@PathVariable String runId,
      @RequestParam(value = "selectedOfferId", required = false) String selectedOfferId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.lockWorkflow(runId, selectedOfferId, uiTraceId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/lock/confirm")
  ResponseEntity<?> confirmLock(@PathVariable String runId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) PricingBffUiFallbackAdapter.LockConfirmRequest request) {
    return adapter.confirmLock(runId, uiTraceId, request);
  }
}
