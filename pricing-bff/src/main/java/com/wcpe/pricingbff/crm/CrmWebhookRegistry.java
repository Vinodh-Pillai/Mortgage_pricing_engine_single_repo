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
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class CrmWebhookRegistry {
  private final Map<String, Registration> registrations = new ConcurrentHashMap<>();
  private final Map<String, WebhookDeliveryReceipt> deliveries = new ConcurrentHashMap<>();
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
    String webhookId = UUID.nameUUIDFromBytes((resolvedTenant + ":" + resolvedSystem + ":" + request.url()).getBytes(StandardCharsets.UTF_8)).toString();
    Registration registration = new Registration(webhookId, resolvedTenant, resolvedSystem, request.url(), request.events(), request.secret(), Instant.now());
    registrations.put(webhookId, registration);
    return registration.toResponse();
  }

  List<WebhookDeliveryReceipt> dispatch(String tenantId, String sourceSystem, WebhookEvent event) {
    List<WebhookDeliveryReceipt> receipts = new ArrayList<>();
    registrations.values().stream()
        .filter(registration -> registration.tenantId().equals(tenantId))
        .filter(registration -> registration.sourceSystem().equalsIgnoreCase(sourceSystem))
        .filter(registration -> registration.events().contains(event.eventType()))
        .forEach(registration -> receipts.add(deliver(registration, event)));
    return receipts;
  }

  List<WebhookDeliveryReceipt> deliveries() {
    return deliveries.values().stream().toList();
  }

  private WebhookDeliveryReceipt deliver(Registration registration, WebhookEvent event) {
    String deliveryId = UUID.randomUUID().toString();
    if (!deliveryEnabled) {
      WebhookDeliveryReceipt receipt = new WebhookDeliveryReceipt(deliveryId, registration.webhookId(), event.eventType(), "QUEUED", 0, Instant.now(), "delivery disabled until local/dev endpoint is configured");
      deliveries.put(deliveryId, receipt);
      return receipt;
    }
    String lastError = null;
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        String payload = objectMapper.writeValueAsString(event.payload());
        HttpRequest request = HttpRequest.newBuilder(URI.create(registration.url()))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .header("X-LoanWeft-Event", event.eventType())
            .header("X-Correlation-ID", event.correlationId())
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
          WebhookDeliveryReceipt receipt = new WebhookDeliveryReceipt(deliveryId, registration.webhookId(), event.eventType(), "DELIVERED", attempt, null, null);
          deliveries.put(deliveryId, receipt);
          return receipt;
        }
        lastError = "HTTP " + response.statusCode();
      } catch (IOException | InterruptedException ex) {
        if (ex instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        lastError = ex.getClass().getSimpleName();
      }
    }
    WebhookDeliveryReceipt receipt = new WebhookDeliveryReceipt(deliveryId, registration.webhookId(), event.eventType(), "DEAD", 3, null, lastError);
    deliveries.put(deliveryId, receipt);
    return receipt;
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private record Registration(String webhookId, String tenantId, String sourceSystem, String url, List<String> events,
      String secret, Instant registeredAt) {
    Registration {
      events = List.copyOf(events == null ? List.of() : events);
    }

    CrmWebhookRegistrationResponse toResponse() {
      return new CrmWebhookRegistrationResponse(webhookId, tenantId, sourceSystem, url, events, "ACTIVE", registeredAt);
    }
  }
}
