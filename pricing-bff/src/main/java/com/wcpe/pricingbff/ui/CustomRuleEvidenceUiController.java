package com.wcpe.pricingbff.ui;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
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

  @GetMapping("/api/ui/custom-rules/fields")
  Object customRuleFields(
      @RequestParam(value = "scenarioId", required = false) String scenarioId,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.customRuleFieldsForUi(scenarioId, tenantContext, uiTraceId);
  }

  @GetMapping("/api/ui/custom-rules/evidence")
  Object customRuleEvidenceForUi(
      @RequestParam(value = "quoteId", required = false) String quoteId,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.customRuleEvidenceForUi(quoteId, tenantContext, uiTraceId);
  }
}
