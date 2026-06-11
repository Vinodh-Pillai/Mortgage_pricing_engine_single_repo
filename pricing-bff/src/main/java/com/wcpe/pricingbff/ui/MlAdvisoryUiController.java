package com.wcpe.pricingbff.ui;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MlAdvisoryUiController {
  private final PricingBffUiFallbackAdapter adapter;

  MlAdvisoryUiController(PricingBffUiFallbackAdapter adapter) {
    this.adapter = adapter;
  }

  @GetMapping("/api/v1/ml-advisory/insights")
  Object mlAdvisoryInsights(
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.mlAdvisoryInsights(tenantContext, uiTraceId);
  }
}
