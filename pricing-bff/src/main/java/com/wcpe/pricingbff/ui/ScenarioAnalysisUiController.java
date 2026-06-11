package com.wcpe.pricingbff.ui;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ScenarioAnalysisUiController {
  private final PricingBffUiFallbackAdapter adapter;

  ScenarioAnalysisUiController(PricingBffUiFallbackAdapter adapter) {
    this.adapter = adapter;
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/what-if/workspace")
  Object scenarioAnalysisWorkspace(@PathVariable String runId,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.scenarioAnalysisWorkspace(tenantContext, runId, uiTraceId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/what-if/recalculate")
  ResponseEntity<?> scenarioAnalysisRecalculate(@PathVariable String runId,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) Map<String, Object> request) {
    return adapter.scenarioAnalysisRecalculate(tenantContext, runId, uiTraceId, request);
  }
}
