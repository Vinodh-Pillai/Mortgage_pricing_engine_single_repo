package com.wcpe.pricingbff.ui;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OpsUiController {
  private final PricingBffUiFallbackAdapter adapter;

  OpsUiController(PricingBffUiFallbackAdapter adapter) {
    this.adapter = adapter;
  }

  @GetMapping("/api/v1/ops/cases")
  Object opsCases(@RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.opsCases(tenantContext, uiTraceId);
  }

  @GetMapping("/api/v1/ops/rate-feeds")
  Object rateFeedOperations(@RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.rateFeedOperations(tenantContext, uiTraceId);
  }

  @GetMapping("/api/v1/ops/performance")
  Object performanceDashboard(@RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.performanceDashboard(tenantContext, uiTraceId);
  }

  @GetMapping("/api/v1/ops/cases/{caseId}")
  Object opsCaseDetail(@PathVariable String caseId,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.opsCaseDetail(caseId, tenantContext, uiTraceId);
  }

  @PostMapping("/api/v1/ops/cases/{caseId}/assign")
  ResponseEntity<?> assignOpsCase(@PathVariable String caseId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) PricingBffUiFallbackAdapter.OpsCaseAssignRequest request) {
    return adapter.assignOpsCase(caseId, uiTraceId, request);
  }

  @PostMapping("/api/v1/ops/cases/{caseId}/notes")
  ResponseEntity<?> addOpsCaseNote(@PathVariable String caseId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) PricingBffUiFallbackAdapter.OpsCaseNoteRequest request) {
    return adapter.addOpsCaseNote(caseId, uiTraceId, request);
  }

  @PostMapping("/api/v1/ops/cases/{caseId}/status")
  ResponseEntity<?> updateOpsCaseStatus(@PathVariable String caseId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) PricingBffUiFallbackAdapter.OpsCaseStatusRequest request) {
    return adapter.updateOpsCaseStatus(caseId, uiTraceId, request);
  }
}
