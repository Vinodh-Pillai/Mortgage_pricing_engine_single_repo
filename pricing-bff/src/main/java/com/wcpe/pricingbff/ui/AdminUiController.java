package com.wcpe.pricingbff.ui;

import java.util.Map;
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
class AdminUiController {
  private final PricingBffUiFallbackAdapter adapter;

  AdminUiController(PricingBffUiFallbackAdapter adapter) {
    this.adapter = adapter;
  }

  @GetMapping("/api/v1/admin/governance")
  Object adminGovernance(@RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Admin-Role", required = false) String adminRole,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.adminGovernance(tenantContext, adminRole, uiTraceId);
  }

  @GetMapping("/api/v1/admin/tenants")
  ResponseEntity<?> tenantAdminList(@RequestParam(value = "search", required = false) String search,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "page", required = false) Integer page,
      @RequestParam(value = "size", required = false) Integer size,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.tenantAdminList(search, status, page, size, uiTraceId);
  }

  @GetMapping("/api/v1/admin/tenants/{tenantId}")
  ResponseEntity<?> tenantAdminRecord(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.tenantAdminRecord(tenantId, uiTraceId);
  }

  @PostMapping("/api/v1/admin/tenants")
  ResponseEntity<?> createTenantAdminRecord(@RequestBody(required = false) Map<String, Object> payload,
      @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.createTenantAdminRecord(payload, actorId, uiTraceId);
  }

  @PatchMapping("/api/v1/admin/tenants/{tenantId}")
  ResponseEntity<?> updateTenantAdminRecord(@PathVariable String tenantId,
      @RequestBody(required = false) Map<String, Object> payload,
      @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.updateTenantAdminRecord(tenantId, payload, actorId, uiTraceId);
  }

  @PostMapping("/api/v1/admin/tenants/{tenantId}/{action:activate|suspend|deactivate}")
  ResponseEntity<?> changeTenantAdminStatus(@PathVariable String tenantId, @PathVariable String action,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.changeTenantAdminStatus(tenantId, action, uiTraceId);
  }

  @GetMapping("/api/v1/admin/tenants/{tenantId}/feature-flags")
  ResponseEntity<?> tenantFeatureFlags(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.tenantFeatureFlags(tenantId, uiTraceId);
  }

  @PatchMapping("/api/v1/admin/tenants/{tenantId}/feature-flags")
  ResponseEntity<?> updateTenantFeatureFlags(@PathVariable String tenantId,
      @RequestBody(required = false) Map<String, Object> payload,
      @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.updateTenantFeatureFlags(tenantId, payload, actorId, uiTraceId);
  }
}
