package com.wcpe.pricingbff.ui;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ExceptionConcessionUiController {
  private final PricingBffUiFallbackAdapter adapter;

  ExceptionConcessionUiController(PricingBffUiFallbackAdapter adapter) {
    this.adapter = adapter;
  }

  @GetMapping("/api/v1/exceptions/concessions/workbench")
  Object exceptionConcessionWorkbench(
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.exceptionConcessionWorkbench(tenantContext, uiTraceId);
  }
}
