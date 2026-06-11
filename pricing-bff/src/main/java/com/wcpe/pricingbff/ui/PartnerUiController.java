package com.wcpe.pricingbff.ui;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PartnerUiController {
  private final PricingBffUiFallbackAdapter adapter;

  PartnerUiController(PricingBffUiFallbackAdapter adapter) {
    this.adapter = adapter;
  }

  @GetMapping("/api/v1/partners/{partnerId}/quotes")
  Object partnerQuotes(@PathVariable String partnerId,
      @RequestParam(value = "status", required = false) String status,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.partnerQuotes(partnerId, status, tenantContext, uiTraceId);
  }

  @GetMapping("/api/v1/partners/{partnerId}/quotes/{quoteId}")
  Object partnerQuoteDetail(@PathVariable String partnerId, @PathVariable String quoteId,
      @RequestParam(value = "apiPermit", required = false, defaultValue = "false") boolean apiPermit,
      @RequestHeader(value = "X-Partner-Role", required = false) String partnerRole,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.partnerQuoteDetail(partnerId, quoteId, apiPermit, partnerRole, tenantContext, uiTraceId);
  }

  @PostMapping("/api/v1/partners/{partnerId}/quotes/{quoteId}/reprice")
  ResponseEntity<?> partnerReprice(@PathVariable String quoteId,
      @RequestParam(value = "apiPermit", required = false, defaultValue = "false") boolean apiPermit,
      @RequestHeader(value = "X-Partner-Role", required = false) String partnerRole,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.partnerReprice(quoteId, apiPermit, partnerRole, uiTraceId);
  }

  @GetMapping("/api/v1/partners/{partnerId}/integrations/webhooks")
  Object partnerWebhookHealth(@PathVariable String partnerId,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.partnerWebhookHealth(partnerId, tenantContext, uiTraceId);
  }

  @GetMapping("/api/v1/partners/{partnerId}/integrations/workbench")
  Object partnerChannelWorkbench(@PathVariable String partnerId,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.partnerChannelWorkbench(partnerId, tenantContext, uiTraceId);
  }

  @PostMapping("/api/v1/partners/{partnerId}/integrations/webhooks/{webhookId}/test")
  ResponseEntity<?> testPartnerWebhook(@PathVariable String webhookId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return adapter.testPartnerWebhook(webhookId, uiTraceId);
  }

  @PostMapping("/api/v1/partners/{partnerId}/integrations/webhooks/{webhookId}/replay")
  ResponseEntity<?> replayPartnerWebhook(@PathVariable String webhookId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) PricingBffUiFallbackAdapter.PartnerWebhookReplayRequest request) {
    return adapter.replayPartnerWebhook(webhookId, uiTraceId, request);
  }

  @PostMapping("/api/v1/partners/{partnerId}/integrations/webhooks/{webhookId}/safety")
  ResponseEntity<?> updatePartnerWebhookSafety(@PathVariable String webhookId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) PricingBffUiFallbackAdapter.PartnerSafetyToggleRequest request) {
    return adapter.updatePartnerWebhookSafety(webhookId, uiTraceId, request);
  }
}
