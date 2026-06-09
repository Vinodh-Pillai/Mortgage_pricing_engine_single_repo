package com.wcpe.pricingbff.ui;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class QualityUiController {
  private final PricingBffUiFallbackAdapter adapter;

  QualityUiController(PricingBffUiFallbackAdapter adapter) {
    this.adapter = adapter;
  }

  @GetMapping("/api/v1/quality/dashboard")
  Object qualityDashboard(@RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Quality-Role", required = false) String qualityRole,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.qualityDashboard(tenantContext, qualityRole, uiTraceId);
  }

  @GetMapping("/api/v1/quality/evidence/export")
  Object qualityEvidenceExport() {
    return adapter.qualityEvidenceExport();
  }
}
