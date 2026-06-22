package com.wcpe.pricingbff.crm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.pricingbff.crm.CrmApiModels.CrmWebhookRegistrationRequest;
import com.wcpe.pricingbff.crm.CrmApiModels.CrmWebhookRegistrationResponse;
import com.wcpe.pricingbff.crm.CrmApiModels.WebhookDeliveryReceipt;
import com.wcpe.pricingbff.crm.CrmApiModels.WebhookEvent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class CrmWebhookRegistry {
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final boolean deliveryEnabled;

  CrmWebhookRegistry(ObjectMapper objectMapper,
      @Value("${loanweft.integrations.crm.webhooks.delivery-enabled:false}") boolean deliveryEnabled) {
    this.objectMapper = objectMapper;
    this.deliveryEnabled = deliveryEnabled;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  }

  CrmWebhookRegistrationResponse register(CrmWebhookRegistrationRequest request, String tenantId, String sourceSystem) {
    if (request.url() == null || request.url().isBlank()) {
      throw new CrmValidationException("CRM_WEBHOOK_URL_REQUIRED", "Webhook url is required");
    }
    if (request.events().isEmpty()) {
      throw new CrmValidationException("CRM_WEBHOOK_EVENTS_REQUIRED", "At least one CRM webhook event is required");
    }
    URI uri = URI.create(request.url());
    if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
      throw new CrmValidationException("CRM_WEBHOOK_URL_INVALID", "Webhook url must use http or https");
    }
    String resolvedTenant = blankToDefault(request.tenantId(), tenantId);
    String resolvedSystem = blankToDefault(request.sourceSystem(), sourceSystem).toUpperCase();
    throw new CrmValidationException("CRM_WEBHOOK_PERSISTENCE_REQUIRED",
        "CRM webhook registration requires durable webhook persistence; process-local webhook registration state is disabled for " + resolvedTenant + ":" + resolvedSystem);
  }

  List<WebhookDeliveryReceipt> dispatch(String tenantId, String sourceSystem, WebhookEvent event) {
    return List.of();
  }

  List<WebhookDeliveryReceipt> deliveries() {
    return List.of();
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

}
