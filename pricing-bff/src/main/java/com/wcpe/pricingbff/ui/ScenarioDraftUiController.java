package com.wcpe.pricingbff.ui;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ScenarioDraftUiController {
  private final PricingBffUiFallbackAdapter adapter;

  ScenarioDraftUiController(PricingBffUiFallbackAdapter adapter) {
    this.adapter = adapter;
  }

  @PostMapping("/api/v1/tenants/{tenantId}/scenarios")
  ResponseEntity<?> createDraftScenario(@PathVariable String tenantId,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) PricingBffUiFallbackAdapter.DraftScenarioRequest request) {
    return adapter.createDraftScenario(tenantId, tenantContext, uiTraceId, request);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/scenarios")
  ResponseEntity<?> findDraftScenarios(@PathVariable String tenantId,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestParam(value = "borrowerLastName", required = false) String borrowerLastName,
      @RequestParam(value = "loanNumber", required = false) String loanNumber,
      @RequestParam(value = "status", required = false) String status) {
    return adapter.findDraftScenarios(tenantId, tenantContext, uiTraceId, borrowerLastName, loanNumber, status);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/scenarios/{scenarioId}")
  ResponseEntity<?> getDraftScenario(@PathVariable String tenantId,
      @PathVariable String scenarioId,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.getDraftScenario(tenantId, scenarioId, tenantContext, uiTraceId);
  }

  @PatchMapping("/api/v1/tenants/{tenantId}/scenarios/{scenarioId}/{section}")
  ResponseEntity<?> updateDraftScenario(@PathVariable String tenantId,
      @PathVariable String scenarioId,
      @PathVariable String section,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) PricingBffUiFallbackAdapter.DraftScenarioRequest request) {
    return adapter.updateDraftScenario(tenantId, scenarioId, section, tenantContext, uiTraceId, request);
  }
}
