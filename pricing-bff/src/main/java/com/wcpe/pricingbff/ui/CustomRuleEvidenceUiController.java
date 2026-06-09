package com.wcpe.pricingbff.ui;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CustomRuleEvidenceUiController {
  private final PricingBffUiFallbackAdapter adapter;

  CustomRuleEvidenceUiController(PricingBffUiFallbackAdapter adapter) {
    this.adapter = adapter;
  }

  @GetMapping("/api/v1/custom-rules/evidence")
  Object customRuleEvidence(
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.customRuleEvidence(tenantContext, uiTraceId);
  }
}
