package com.wcpe.pricingbff.ui;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PipelineSettingsUiController {
  private final PricingBffUiFallbackAdapter adapter;

  PipelineSettingsUiController(PricingBffUiFallbackAdapter adapter) {
    this.adapter = adapter;
  }

  @GetMapping("/api/v1/tenants/{tenantId}/pipeline/settings")
  Object pipelineSettings(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.pipelineSettings(tenantId, uiTraceId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/pipeline/settings")
  ResponseEntity<?> savePipelineSettings(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) PricingBffUiFallbackAdapter.PipelineSettingsRequest request) {
    return adapter.savePipelineSettings(tenantId, uiTraceId, request);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/pipeline/client-settings")
  Object clientSettings(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.clientSettings(tenantId, uiTraceId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/pipeline/client-settings")
  ResponseEntity<?> saveClientSettings(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) PricingBffUiFallbackAdapter.ClientSettingsRequest request) {
    return adapter.saveClientSettings(tenantId, uiTraceId, request);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/pipeline/client-settings/publish")
  ResponseEntity<?> publishClientSettings(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.publishClientSettings(tenantId, uiTraceId);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/pipeline/pricing-notifications")
  Object notificationSettings(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.notificationSettings(tenantId, uiTraceId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/pipeline/pricing-notifications")
  ResponseEntity<?> saveNotificationSettings(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) PricingBffUiFallbackAdapter.NotificationSettingsRequest request) {
    return adapter.saveNotificationSettings(tenantId, uiTraceId, request);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/pipeline/pricing-notifications/publish")
  ResponseEntity<?> publishNotificationSettings(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.publishNotificationSettings(tenantId, uiTraceId);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/pipeline/pricing-access")
  Object pricingAccessSettings(@PathVariable String tenantId,
      @RequestHeader(value = "X-User-Role", required = false) String userRoleId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.pricingAccessSettings(tenantId, userRoleId, uiTraceId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/pipeline/pricing-access")
  ResponseEntity<?> savePricingAccessSettings(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestHeader(value = "X-User-Id", required = false) String actorUserId,
      @RequestBody(required = false) PricingBffUiFallbackAdapter.PricingAccessSettingsRequest request) {
    return adapter.savePricingAccessSettings(tenantId, uiTraceId, actorUserId, request);
  }
}
