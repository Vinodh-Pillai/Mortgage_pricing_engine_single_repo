package com.wcpe.pricingbff.ui;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class QuoteRunUiController {
  private final PricingBffUiFallbackAdapter adapter;

  QuoteRunUiController(PricingBffUiFallbackAdapter adapter) {
    this.adapter = adapter;
  }

  @PostMapping("/api/v1/tenants/{tenantId}/quote-runs")
  ResponseEntity<?> launchQuoteRun(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) Map<String, Object> intake) {
    return adapter.launchQuoteRun(tenantId, uiTraceId, intake);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/intake-metadata")
  Object scenarioIntakeMetadata(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.scenarioIntakeMetadata(tenantId, uiTraceId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/intake/validate")
  Object validateQuoteRunIntake(@RequestBody(required = false) Map<String, Object> intake) {
    return adapter.validateQuoteRunIntake(intake);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/status")
  Object quoteRunStatus(@PathVariable String runId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.quoteRunStatus(runId, uiTraceId);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/pricing-waterfall")
  Object pricingWaterfall(@PathVariable String tenantId, @PathVariable String runId,
      @RequestParam(value = "selectedOfferId", required = false) String selectedOfferId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.pricingWaterfall(tenantId, runId, selectedOfferId, uiTraceId);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/journey")
  Object quoteJourneyMap(@PathVariable String tenantId, @PathVariable String runId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.quoteJourneyMap(tenantId, runId, uiTraceId);
  }
}
