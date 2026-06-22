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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class LosWebhookRegistry {
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
    throw new LosValidationException("WEBHOOK_PERSISTENCE_REQUIRED",
        "LOS webhook registration requires durable webhook persistence; process-local webhook registration state is disabled for tenant " + tenantId);
  }

  List<WebhookDeliveryReceipt> dispatch(String tenantId, WebhookEvent event) {
    return dispatch(tenantId, event, true, null);
  }

  List<WebhookDeliveryReceipt> dispatch(String tenantId, WebhookEvent event, boolean callbackDeliveryEnabled, String disabledAuditRef) {
    return List.of();
  }

  List<WebhookDeliveryReceipt> deliveries() {
    return List.of();
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
