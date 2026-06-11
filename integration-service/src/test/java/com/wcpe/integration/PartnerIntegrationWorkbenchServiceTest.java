package com.wcpe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.integration.PartnerIntegrationWorkbenchService.ActionResult;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.DependencyName;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.DependencyState;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.DependencyStatus;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.DeliveryAttempt;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.DeliveryAttemptStatus;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.IdempotencyState;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.LockState;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.PartnerQuote;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.PartnerQuoteDetail;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.PartnerQuoteListItem;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.PartnerRepriceResult;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.PartnerWebhookHealthView;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.QuoteStatus;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.RepriceCommand;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.RootCauseCode;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.SafetyState;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.SafetyToggleResult;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.SlaState;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.WebhookActionCommand;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.WebhookIntegration;
import com.wcpe.integration.PartnerIntegrationWorkbenchService.WebhookSafetyCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PartnerIntegrationWorkbenchServiceTest {
  private static final String PARTNER_ID = "partner-alpha";
  private static final String QUOTE_ID = "quote-1001";
  private static final String WEBHOOK_ID = "webhook-main";
  private static final String CORRELATION_ID = "corr-PII-24-S38";

  @Test
  void filtersByStatus() {
    PartnerIntegrationWorkbenchService service = service();
    service.upsertQuote(quote(QUOTE_ID, QuoteStatus.PRICED, SlaState.AT_RISK, LockState.UNLOCKED, true, availableDependencies()));
    service.upsertQuote(quote("quote-locked", QuoteStatus.LOCKED, SlaState.ON_TRACK, LockState.LOCKED, false, availableDependencies()));

    List<PartnerQuoteListItem> priced = service.quotes(PARTNER_ID, QuoteStatus.PRICED, CORRELATION_ID).value().orElseThrow();

    assertEquals(1, priced.size());
    assertEquals(QUOTE_ID, priced.get(0).quoteId());
    assertEquals(SlaState.AT_RISK, priced.get(0).slaState());
    assertEquals(LockState.UNLOCKED, priced.get(0).lockState());
    assertTrue(priced.get(0).metadata().auditRefs().get(0).startsWith("audit:"));
    assertEquals(CORRELATION_ID, priced.get(0).metadata().correlationId());
    assertFalse(priced.get(0).metadata().replayHash().isBlank());
  }

  @Test
  void quoteDetailExposesLifecycleAndVersionMetadata() {
    PartnerIntegrationWorkbenchService service = service();
    service.upsertQuote(quote(QUOTE_ID, QuoteStatus.COMMITTED, SlaState.ON_TRACK, LockState.RELOCKED, true, availableDependencies()));

    PartnerQuoteDetail detail = service.quoteDetail(PARTNER_ID, QUOTE_ID, CORRELATION_ID).value().orElseThrow();

    assertEquals(List.of(QuoteStatus.SUBMITTED, QuoteStatus.PRICING, QuoteStatus.PRICED, QuoteStatus.COMMITTED), detail.lifecycle());
    assertEquals("partner-integration-api:v1", detail.metadata().versionRefs().get(0));
    assertEquals(DependencyState.AVAILABLE, detail.metadata().dependencyStatus().statusByDependency().get(DependencyName.PRICING_SERVICE.name()));
  }

  @Test
  void repriceWithGuidance() {
    PartnerIntegrationWorkbenchService service = service();
    service.upsertQuote(quote(QUOTE_ID, QuoteStatus.PRICED, SlaState.AT_RISK, LockState.UNLOCKED, true, availableDependencies()));

    PartnerRepriceResult result = service.reprice(new RepriceCommand(PARTNER_ID, QUOTE_ID, "pricing-ops", CORRELATION_ID)).value().orElseThrow();

    assertTrue(result.accepted());
    assertEquals("REPRICE_REQUEST_ACCEPTED", result.reason());
    assertEquals("refresh price through configured pricing dependency", result.guidance());
    assertEquals("PartnerQuoteRepriced.v1", service.outboxEvents().get(0).eventType());
  }

  @Test
  void repriceBlocksWhenPricingDependencyUnavailableWithoutInventingDecision() {
    PartnerIntegrationWorkbenchService service = service();
    service.upsertQuote(quote(QUOTE_ID, QuoteStatus.PRICED, SlaState.BREACHED, LockState.EXPIRED, true, new DependencyStatus(Map.of(DependencyName.PRICING_SERVICE.name(), DependencyState.UNAVAILABLE))));

    PartnerRepriceResult result = service.reprice(new RepriceCommand(PARTNER_ID, QUOTE_ID, "pricing-ops", CORRELATION_ID)).value().orElseThrow();

    assertFalse(result.accepted());
    assertEquals("DEPENDENCY_OR_POLICY_BLOCKED", result.reason());
    assertEquals(DependencyState.UNAVAILABLE, result.metadata().dependencyStatus().statusByDependency().get(DependencyName.PRICING_SERVICE.name()));
  }

  @Test
  void webhookHealthWithDLQ() {
    PartnerIntegrationWorkbenchService service = service();
    service.upsertWebhook(webhook(SafetyState.RESUMED, availableDependencies()));

    PartnerWebhookHealthView health = service.webhookHealth(PARTNER_ID, CORRELATION_ID).value().orElseThrow();

    assertEquals(1, health.webhooks().size());
    assertEquals(2, health.webhooks().get(0).dlqDepth());
    assertEquals(RootCauseCode.HTTP_5XX, health.webhooks().get(0).lastRootCause());
    assertEquals(DeliveryAttemptStatus.DLQ, health.webhooks().get(0).deliveryAttempts().get(1).status());
  }

  @Test
  void channelWorkbenchTabs() {
    PartnerIntegrationWorkbenchService service = service();

    PartnerIntegrationWorkbenchService.PartnerChannelWorkbenchView workbench = service.workbench(PARTNER_ID, CORRELATION_ID).value().orElseThrow();

    assertEquals(6, workbench.tabs().size());
    assertEquals("quotes", workbench.tabs().get(0).key());
    assertEquals("security-ops", workbench.tabs().get(3).recoveryOwner());
    assertEquals(CORRELATION_ID, workbench.metadata().correlationId());
  }

  @Test
  void replayIdempotency() {
    PartnerIntegrationWorkbenchService service = service();
    service.upsertWebhook(webhook(SafetyState.RESUMED, availableDependencies()));
    WebhookActionCommand command = new WebhookActionCommand(PARTNER_ID, WEBHOOK_ID, "integration-ops", "idem-replay", CORRELATION_ID);

    ActionResult replayed = service.replay(command).value().orElseThrow();
    ActionResult duplicate = service.replay(command).value().orElseThrow();

    assertTrue(replayed.accepted());
    assertEquals(IdempotencyState.NEW, replayed.idempotencyState());
    assertEquals(replayed, duplicate);
    assertEquals("PartnerWebhookReplayed.v1", service.outboxEvents().get(0).eventType());
  }

  @Test
  void replayIdempotencyConflictWhenKeyIsReusedForDifferentRequestContent() {
    PartnerIntegrationWorkbenchService service = service();
    service.upsertWebhook(webhook(SafetyState.RESUMED, availableDependencies()));
    WebhookActionCommand original = new WebhookActionCommand(PARTNER_ID, WEBHOOK_ID, "integration-ops", "idem-replay", CORRELATION_ID);
    WebhookActionCommand conflicting = new WebhookActionCommand(PARTNER_ID, WEBHOOK_ID, "audit-ops", "idem-replay", CORRELATION_ID);

    ActionResult replayed = service.replay(original).value().orElseThrow();
    PartnerIntegrationWorkbenchService.PartnerResult<ActionResult> conflict = service.replay(conflicting);

    assertTrue(replayed.accepted());
    assertFalse(conflict.valid());
    assertEquals("409", conflict.error().orElseThrow().code());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.error().orElseThrow().reason());
    assertEquals(1, service.outboxEvents().size());
  }

  @Test
  void safetyToggleConfirmation() {
    PartnerIntegrationWorkbenchService service = service();
    service.upsertWebhook(webhook(SafetyState.RESUMED, availableDependencies()));

    SafetyToggleResult result = service.safety(new WebhookSafetyCommand(PARTNER_ID, WEBHOOK_ID, SafetyState.PAUSED, "integration-ops", CORRELATION_ID)).value().orElseThrow();

    assertEquals(SafetyState.PAUSED, result.safetyState());
    assertEquals("new deliveries queued in DLQ", result.effect());
    assertEquals(SafetyState.PAUSED, service.webhookHealth(PARTNER_ID, CORRELATION_ID).value().orElseThrow().webhooks().get(0).safetyState());
    assertEquals("PartnerWebhookSafetyToggled.v1", service.outboxEvents().get(0).eventType());
  }

  @Test
  void endpointTestResult() {
    PartnerIntegrationWorkbenchService service = service();
    service.upsertWebhook(webhook(SafetyState.RESUMED, availableDependencies()));

    ActionResult result = service.testEndpoint(new WebhookActionCommand(PARTNER_ID, WEBHOOK_ID, "integration-ops", "idem-test", CORRELATION_ID)).value().orElseThrow();

    assertTrue(result.accepted());
    assertEquals("ENDPOINT_TEST_ACCEPTED", result.reason());
    assertEquals("PartnerWebhookTested.v1", service.outboxEvents().get(0).eventType());
  }

  private PartnerIntegrationWorkbenchService service() {
    return new PartnerIntegrationWorkbenchService(Clock.fixed(Instant.parse("2026-06-11T15:30:00Z"), ZoneOffset.UTC));
  }

  private PartnerQuote quote(String quoteId, QuoteStatus status, SlaState slaState, LockState lockState, boolean repriceAvailable, DependencyStatus dependencyStatus) {
    return new PartnerQuote(PARTNER_ID, quoteId, status, List.of(QuoteStatus.SUBMITTED, QuoteStatus.PRICING, QuoteStatus.PRICED, status), slaState, lockState, Set.of("no-credential-exposure"), repriceAvailable, "refresh price through configured pricing dependency", dependencyStatus, Instant.parse("2026-06-11T15:29:00Z"));
  }

  private WebhookIntegration webhook(SafetyState safetyState, DependencyStatus dependencyStatus) {
    return new WebhookIntegration(
        PARTNER_ID,
        WEBHOOK_ID,
        safetyState,
        List.of(
            new DeliveryAttempt("attempt-1", DeliveryAttemptStatus.RETRYING, RootCauseCode.HTTP_5XX, Instant.parse("2026-06-11T15:20:00Z")),
            new DeliveryAttempt("attempt-2", DeliveryAttemptStatus.DLQ, RootCauseCode.HTTP_5XX, Instant.parse("2026-06-11T15:25:00Z"))),
        2,
        RootCauseCode.HTTP_5XX,
        dependencyStatus);
  }

  private DependencyStatus availableDependencies() {
    return DependencyStatus.available(DependencyName.PARTNER_QUOTE_SERVICE, DependencyName.PRICING_SERVICE, DependencyName.LOCK_SERVICE, DependencyName.NOTIFICATIONS, DependencyName.WEBHOOK_ROUTING);
  }
}
