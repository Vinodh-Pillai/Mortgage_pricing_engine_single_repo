package com.wcpe.pricingbff.ui;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class EligibilityUiController {
  private final PricingBffUiFallbackAdapter adapter;

  EligibilityUiController(PricingBffUiFallbackAdapter adapter) {
    this.adapter = adapter;
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/eligibility")
  Object eligibilityModule(@PathVariable String runId,
      @RequestParam(value = "quoteOptionId", required = false) String quoteOptionId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.eligibilityModule(runId, quoteOptionId, uiTraceId);
  }
}
