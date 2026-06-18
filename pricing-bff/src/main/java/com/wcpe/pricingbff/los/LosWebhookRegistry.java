package com.wcpe.pricingbff.los;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.pricingbff.los.LosApiModels.LosWebhookRegistrationRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosWebhookRegistrationResponse;
import com.wcpe.pricingbff.los.LosApiModels.WebhookDeliveryReceipt;
import com.wcpe.pricingbff.los.LosApiModels.WebhookEvent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class LosWebhookRegistry {
  private final Map<String, Registration> registrations = new ConcurrentHashMap<>();
  private final Map<String, WebhookDeliveryReceipt> deliveries = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final boolean deliveryEnabled;

  LosWebhookRegistry(ObjectMapper objectMapper,
      @Value("${loanweft.integrations.los.webhooks.delivery-enabled:false}") boolean deliveryEnabled) {
    this.objectMapper = objectMapper;
    this.deliveryEnabled = deliveryEnabled;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  }

  LosWebhookRegistrationResponse register(LosWebhookRegistrationRequest request, String fallbackTenantId) {
    if (request.url() == null || request.url().isBlank()) {
      throw new LosValidationException("WEBHOOK_URL_REQUIRED", "Webhook url is required");
    }
    if (request.events().isEmpty()) {
      throw new LosValidationException("WEBHOOK_EVENTS_REQUIRED", "At least one webhook event is required");
    }
    URI uri = URI.create(request.url());
    if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
      throw new LosValidationException("WEBHOOK_URL_INVALID", "Webhook url must use http or https");
    }
    String tenantId = blankToDefault(request.tenantId(), fallbackTenantId);
    String webhookId = UUID.nameUUIDFromBytes((tenantId + ":" + request.url()).getBytes(StandardCharsets.UTF_8)).toString();
    String signingMaterial = firstConfigured(request.signingCredentialRef(), request.secret());
    Registration registration = new Registration(webhookId, tenantId, request.url(), request.events(), signingMaterial, Instant.now());
    registrations.put(webhookId, registration);
    return registration.toResponse();
  }

  List<WebhookDeliveryReceipt> dispatch(String tenantId, WebhookEvent event) {
    return dispatch(tenantId, event, true, null);
  }

  List<WebhookDeliveryReceipt> dispatch(String tenantId, WebhookEvent event, boolean callbackDeliveryEnabled, String disabledAuditRef) {
    List<WebhookDeliveryReceipt> receipts = new ArrayList<>();
    registrations.values().stream()
        .filter(registration -> registration.tenantId().equals(tenantId))
        .filter(registration -> registration.events().contains(event.eventType()))
        .forEach(registration -> receipts.add(callbackDeliveryEnabled
            ? deliver(registration, event)
            : recordDisabledDelivery(registration, event, disabledAuditRef)));
    return receipts;
  }

  List<WebhookDeliveryReceipt> deliveries() {
    return deliveries.values().stream().toList();
  }

  private WebhookDeliveryReceipt deliver(Registration registration, WebhookEvent event) {
    String payload;
    try {
      payload = objectMapper.writeValueAsString(event.payload());
    } catch (IOException ex) {
      payload = event.payload().toString();
    }
    String idempotencyKey = idempotencyKey(registration, event);
    String deliveryId = UUID.nameUUIDFromBytes((registration.webhookId() + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8)).toString();
    WebhookDeliveryReceipt existing = deliveries.get(deliveryId);
    if (existing != null) {
      return existing;
    }
    String auditRef = "webhook-delivery:" + deliveryId;
    if (registration.secret() == null || registration.secret().isBlank()) {
      WebhookDeliveryReceipt receipt = new WebhookDeliveryReceipt(deliveryId, registration.webhookId(), event.eventType(),
          "BLOCKED", 0, null, "webhook signing credential reference is required", idempotencyKey, null, auditRef);
      deliveries.put(deliveryId, receipt);
      return receipt;
    }
    String signatureHeader = signature(payload, registration.secret());
    if (!deliveryEnabled) {
      WebhookDeliveryReceipt receipt = new WebhookDeliveryReceipt(deliveryId, registration.webhookId(), event.eventType(),
          "QUEUED", 0, Instant.now(), "delivery disabled until local/dev endpoint is configured", idempotencyKey,
          signatureHeader, auditRef);
      deliveries.put(deliveryId, receipt);
      return receipt;
    }
    String lastError = null;
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        HttpRequest request = HttpRequest.newBuilder(URI.create(registration.url()))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .header("X-LoanWeft-Event", event.eventType())
            .header("X-Correlation-ID", event.correlationId())
            .header("X-LoanWeft-Delivery-Id", deliveryId)
            .header("X-LoanWeft-Idempotency-Key", idempotencyKey)
            .header("X-LoanWeft-Signature", signatureHeader)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
          WebhookDeliveryReceipt receipt = new WebhookDeliveryReceipt(deliveryId, registration.webhookId(), event.eventType(),
              "DELIVERED", attempt, null, null, idempotencyKey, signatureHeader, auditRef);
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
    WebhookDeliveryReceipt receipt = new WebhookDeliveryReceipt(deliveryId, registration.webhookId(), event.eventType(),
        "DEAD", 3, null, lastError, idempotencyKey, signatureHeader, auditRef);
    deliveries.put(deliveryId, receipt);
    return receipt;
  }

  private WebhookDeliveryReceipt recordDisabledDelivery(Registration registration, WebhookEvent event, String disabledAuditRef) {
    String idempotencyKey = idempotencyKey(registration, event);
    String deliveryId = UUID.nameUUIDFromBytes((registration.webhookId() + ":" + idempotencyKey + ":disabled").getBytes(StandardCharsets.UTF_8)).toString();
    WebhookDeliveryReceipt existing = deliveries.get(deliveryId);
    if (existing != null) {
      return existing;
    }
    String auditRef = disabledAuditRef == null || disabledAuditRef.isBlank()
        ? "webhook-delivery-disabled:" + deliveryId
        : disabledAuditRef + ":callback-delivery-disabled:" + deliveryId;
    WebhookDeliveryReceipt receipt = new WebhookDeliveryReceipt(deliveryId, registration.webhookId(), event.eventType(),
        "DISABLED", 0, null, "callback delivery disabled by tenant feature flag", idempotencyKey, null, auditRef);
    deliveries.put(deliveryId, receipt);
    return receipt;
  }

  private String idempotencyKey(Registration registration, WebhookEvent event) {
    Object quoteJobId = event.payload().get("quoteJobId");
    Object status = event.payload().get("status");
    Object pricingRequestId = event.payload().get("pricingRequestId");
    String aggregate = quoteJobId == null ? String.valueOf(pricingRequestId) : String.valueOf(quoteJobId);
    return registration.webhookId() + ":" + event.eventType() + ":" + aggregate + ":" + status;
  }

  private String signature(String payload, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return "sha256=" + Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
      throw new LosValidationException("WEBHOOK_SIGNATURE_FAILED", "Webhook signature could not be generated from configured credential reference");
    }
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String firstConfigured(String preferred, String fallback) {
    if (preferred != null && !preferred.isBlank()) {
      return preferred;
    }
    return fallback;
  }

  private record Registration(String webhookId, String tenantId, String url, List<String> events, String secret, Instant registeredAt) {
    Registration {
      events = List.copyOf(events == null ? List.of() : events);
    }

    LosWebhookRegistrationResponse toResponse() {
      return new LosWebhookRegistrationResponse(webhookId, tenantId, url, events, "ACTIVE", registeredAt);
    }
  }
}
