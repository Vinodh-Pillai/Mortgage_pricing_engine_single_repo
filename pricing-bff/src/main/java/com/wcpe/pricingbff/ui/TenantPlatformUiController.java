package com.wcpe.pricingbff.ui;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TenantPlatformUiController {
  private final PricingBffUiFallbackAdapter adapter;

  TenantPlatformUiController(PricingBffUiFallbackAdapter adapter) {
    this.adapter = adapter;
  }

  @GetMapping("/api/v1/platform/tenant-context")
  Object tenantPlatformCoverage(
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.tenantPlatformCoverage(tenantContext, uiTraceId);
  }
}
