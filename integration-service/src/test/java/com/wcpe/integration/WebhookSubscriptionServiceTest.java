package com.wcpe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.integration.WebhookSubscriptionService.ChannelWebhookPolicy;
import com.wcpe.integration.WebhookSubscriptionService.CreateWebhookSubscriptionCommand;
import com.wcpe.integration.WebhookSubscriptionService.RetryPolicyBounds;
import com.wcpe.integration.WebhookSubscriptionService.RotateWebhookSecretCommand;
import com.wcpe.integration.WebhookSubscriptionService.SignedWebhookEnvelope;
import com.wcpe.integration.WebhookSubscriptionService.TestWebhookSubscriptionCommand;
import com.wcpe.integration.WebhookSubscriptionService.UpdateWebhookSubscriptionCommand;
import com.wcpe.integration.WebhookSubscriptionService.WebhookRetryPolicy;
import com.wcpe.integration.WebhookSubscriptionService.WebhookResult;
import com.wcpe.integration.WebhookSubscriptionService.WebhookSubscriptionResponse;
import com.wcpe.integration.WebhookSubscriptionService.WebhookSubscriptionStatus;
import com.wcpe.integration.WebhookSubscriptionService.WebhookTestResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WebhookSubscriptionServiceTest {
  private static final String TENANT_ONE = "11111111-1111-1111-1111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-2222-2222-222222222222";
  private static final String CHANNEL_ID = "los-main";
  private static final String EVENT_TYPE = "pricing.quote.completed.v1";
  private static final String SECRET_REF = "vault://tenant-one/webhooks/los-main";

  @Test
  void createsActiveTenantScopedSubscriptionWithAuditOutboxAndMetrics() {
    WebhookSubscriptionService service = service(Set.of(SECRET_REF));

    WebhookResult<WebhookSubscriptionResponse> result = service.create(command("idem-1", "https://hooks.example.com/wcpe", WebhookSubscriptionStatus.ACTIVE));

    assertTrue(result.valid());
    WebhookSubscriptionResponse response = result.value().orElseThrow();
    assertEquals(WebhookSubscriptionStatus.ACTIVE, response.status());
    assertEquals("secret-version-1", response.secretVersion());
    assertEquals("corr-PII-16-S03", response.correlationId());
    assertEquals(1, service.subscriptionsForTenant(TENANT_ONE).size());
    assertEquals(0, service.subscriptionsForTenant(TENANT_TWO).size());
    assertEquals(WebhookSubscriptionService.CREATED_EVENT_TYPE, service.outboxEvents().get(0).eventType());
    assertEquals(WebhookSubscriptionService.AUDIT_CREATED_ACTION, service.auditRecords().get(0).action());
    assertFalse(service.outboxEvents().get(0).payload().toString().contains("hooks.example.com"));
    assertEquals(1L, service.metrics().get("webhook_subscriptions_active"));
  }

  @Test
  void replaysSameIdempotencyKeyAndRejectsChangedBody() {
    WebhookSubscriptionService service = service(Set.of(SECRET_REF));

    WebhookSubscriptionResponse first = service.create(command("idem-1", "https://hooks.example.com/wcpe", WebhookSubscriptionStatus.ACTIVE)).value().orElseThrow();
    WebhookSubscriptionResponse replay = service.create(command("idem-1", "https://hooks.example.com/wcpe", WebhookSubscriptionStatus.ACTIVE)).value().orElseThrow();
    WebhookResult<WebhookSubscriptionResponse> conflict = service.create(command("idem-1", "https://hooks.example.com/changed", WebhookSubscriptionStatus.ACTIVE));

    assertEquals(first, replay);
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.error().orElseThrow().reason());
    assertEquals(1, service.outboxEvents().size());
  }

  @Test
  void failsClosedForMissingPolicySecretAndInvalidEventType() {
    WebhookSubscriptionService noPolicy =
        new WebhookSubscriptionService(
            fixedClock(),
            new WebhookSubscriptionService.SigningSecretProvider() {
              @Override
              public boolean hasSecret(String secretRef) {
                return true;
              }

              @Override
              public String secretVersion(String secretRef) {
                return "secret-version-1";
              }
            },
            (secretRef, timestamp, body) -> new SignedWebhookEnvelope("sig", Map.of()));
    assertEquals("POLICY_NOT_SATISFIED", noPolicy.create(command("idem-1", "https://hooks.example.com/wcpe", WebhookSubscriptionStatus.ACTIVE)).error().orElseThrow().reason());

    WebhookSubscriptionService noSecret = service(Set.of());
    assertEquals("POLICY_NOT_SATISFIED", noSecret.create(command("idem-1", "https://hooks.example.com/wcpe", WebhookSubscriptionStatus.ACTIVE)).error().orElseThrow().reason());

    WebhookSubscriptionService service = service(Set.of(SECRET_REF));
    CreateWebhookSubscriptionCommand disallowedEvent =
        new CreateWebhookSubscriptionCommand(
            TENANT_ONE,
            CHANNEL_ID,
            "idem-2",
            "integration-admin",
            "LOS quote listener",
            "https://hooks.example.com/wcpe",
            List.of("unconfigured.event.v1"),
            WebhookSubscriptionStatus.ACTIVE,
            retryPolicy(),
            SECRET_REF,
            false,
            "corr-PII-16-S03");

    assertEquals("POLICY_NOT_SATISFIED", service.create(disallowedEvent).error().orElseThrow().reason());
    assertTrue(service.auditRecords().isEmpty());
  }

  @Test
  void enforcesEndpointSecurityAndLocalDevException() {
    WebhookSubscriptionService service = service(Set.of(SECRET_REF));

    assertEquals("POLICY_NOT_SATISFIED", service.create(command("idem-1", "http://hooks.example.com/wcpe", WebhookSubscriptionStatus.ACTIVE)).error().orElseThrow().reason());
    assertEquals("POLICY_NOT_SATISFIED", service.create(command("idem-2", "https://169.254.169.254/latest", WebhookSubscriptionStatus.ACTIVE)).error().orElseThrow().reason());

    CreateWebhookSubscriptionCommand localDev =
        new CreateWebhookSubscriptionCommand(
            TENANT_ONE,
            CHANNEL_ID,
            "idem-3",
            "integration-admin",
            "Local dev listener",
            "http://localhost:18080/webhooks",
            List.of(EVENT_TYPE),
            WebhookSubscriptionStatus.DRAFT,
            retryPolicy(),
            SECRET_REF,
            true,
            "corr-PII-16-S03");

    assertTrue(service.create(localDev).valid());
  }

  @Test
  void updatesPausesRotatesSecretAndQueuesSignedTestEvent() {
    WebhookSubscriptionService service = service(Set.of(SECRET_REF, "vault://tenant-one/webhooks/rotated"));
    WebhookSubscriptionResponse created = service.create(command("idem-1", "https://hooks.example.com/wcpe", WebhookSubscriptionStatus.ACTIVE)).value().orElseThrow();

    WebhookSubscriptionResponse paused =
        service
            .update(
                new UpdateWebhookSubscriptionCommand(
                    TENANT_ONE,
                    CHANNEL_ID,
                    created.id(),
                    "idem-2",
                    "integration-admin",
                    "LOS quote listener paused",
                    "https://hooks.example.com/wcpe",
                    List.of(EVENT_TYPE),
                    WebhookSubscriptionStatus.PAUSED,
                    retryPolicy(),
                    1,
                    false,
                    "corr-PII-16-S03"))
            .value()
            .orElseThrow();
    assertEquals(WebhookSubscriptionStatus.PAUSED, paused.status());

    WebhookSubscriptionResponse reactivated =
        service
            .update(
                new UpdateWebhookSubscriptionCommand(
                    TENANT_ONE,
                    CHANNEL_ID,
                    created.id(),
                    "idem-3",
                    "integration-admin",
                    "LOS quote listener active",
                    "https://hooks.example.com/wcpe",
                    List.of(EVENT_TYPE),
                    WebhookSubscriptionStatus.ACTIVE,
                    retryPolicy(),
                    2,
                    false,
                    "corr-PII-16-S03"))
            .value()
            .orElseThrow();

    WebhookSubscriptionResponse rotated =
        service
            .rotateSecret(
                new RotateWebhookSecretCommand(
                    TENANT_ONE,
                    CHANNEL_ID,
                    reactivated.id(),
                    "idem-4",
                    "integration-admin",
                    3,
                    "vault://tenant-one/webhooks/rotated",
                    "corr-PII-16-S03"))
            .value()
            .orElseThrow();
    assertEquals("secret-version-1", rotated.secretVersion());

    WebhookResult<WebhookTestResponse> test =
        service.test(new TestWebhookSubscriptionCommand(TENANT_ONE, CHANNEL_ID, reactivated.id(), "idem-5", "integration-admin", "corr-PII-16-S03"));

    assertTrue(test.valid());
    assertEquals(WebhookSubscriptionService.TEST_EVENT_TYPE, test.value().orElseThrow().eventType());
    assertEquals("signed-vault://tenant-one/webhooks/rotated", test.value().orElseThrow().signature());
    assertEquals(1, service.testDeliveries().size());
    assertEquals(WebhookSubscriptionService.SECRET_ROTATED_EVENT_TYPE, service.outboxEvents().get(3).eventType());
    assertEquals(1L, service.metrics().get("webhook_test_deliveries_total"));
  }

  @Test
  void preventsDuplicateActiveEndpointEventSetAndTenantCrossRead() {
    WebhookSubscriptionService service = service(Set.of(SECRET_REF));
    WebhookSubscriptionResponse created = service.create(command("idem-1", "https://hooks.example.com/wcpe", WebhookSubscriptionStatus.ACTIVE)).value().orElseThrow();

    assertFalse(service.create(command("idem-2", "https://hooks.example.com/wcpe", WebhookSubscriptionStatus.ACTIVE)).valid());
    assertTrue(service.fetch(TENANT_ONE, CHANNEL_ID, created.id(), "corr-PII-16-S03").valid());
    assertFalse(service.fetch(TENANT_TWO, CHANNEL_ID, created.id(), "corr-PII-16-S03").valid());
  }

  private WebhookSubscriptionService service(Set<String> availableSecrets) {
    WebhookSubscriptionService service =
        new WebhookSubscriptionService(
            fixedClock(),
            new WebhookSubscriptionService.SigningSecretProvider() {
              @Override
              public boolean hasSecret(String secretRef) {
                return availableSecrets.contains(secretRef);
              }

              @Override
              public String secretVersion(String secretRef) {
                return availableSecrets.contains(secretRef) ? "secret-version-1" : "";
              }
            },
            (secretRef, timestamp, body) -> new SignedWebhookEnvelope("signed-" + secretRef, Map.of("X-Integration-Signature", "signed-" + secretRef)));
    service.configureChannelPolicy(
        TENANT_ONE,
        CHANNEL_ID,
        new ChannelWebhookPolicy(
            List.of(EVENT_TYPE),
            new RetryPolicyBounds(
                1,
                5,
                Duration.ofSeconds(1),
                Duration.ofMinutes(5),
                Duration.ofSeconds(30))));
    return service;
  }

  private Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-06-04T03:00:00Z"), ZoneOffset.UTC);
  }

  private CreateWebhookSubscriptionCommand command(String idempotencyKey, String endpointUrl, WebhookSubscriptionStatus status) {
    return new CreateWebhookSubscriptionCommand(
        TENANT_ONE,
        CHANNEL_ID,
        idempotencyKey,
        "integration-admin",
        "LOS quote listener",
        endpointUrl,
        List.of(EVENT_TYPE),
        status,
        retryPolicy(),
        SECRET_REF,
        false,
        "corr-PII-16-S03");
  }

  private WebhookRetryPolicy retryPolicy() {
    return new WebhookRetryPolicy(3, Duration.ofSeconds(2), Duration.ofMinutes(1), Duration.ofSeconds(10));
  }
}
