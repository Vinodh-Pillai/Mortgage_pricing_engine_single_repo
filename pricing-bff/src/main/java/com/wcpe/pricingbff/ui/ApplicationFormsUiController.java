package com.wcpe.pricingbff.ui;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ApplicationFormsUiController {
  private final PricingBffUiFallbackAdapter adapter;

  ApplicationFormsUiController(PricingBffUiFallbackAdapter adapter) {
    this.adapter = adapter;
  }

  @GetMapping("/api/v1/tenants/{tenantId}/application-forms/active")
  ResponseEntity<?> activeApplicationForm(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.activeApplicationForm(tenantId, uiTraceId);
  }
}
