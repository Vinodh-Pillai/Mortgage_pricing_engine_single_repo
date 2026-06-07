package com.wcpe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.integration.WebhookDeliveryService.EnqueueWebhookDeliveryCommand;
import com.wcpe.integration.WebhookDeliveryService.ManualWebhookRetryCommand;
import com.wcpe.integration.WebhookDeliveryService.SearchWebhookDeliveriesQuery;
import com.wcpe.integration.WebhookDeliveryService.SignedWebhookPayload;
import com.wcpe.integration.WebhookDeliveryService.WebhookDeliveryHttpResponse;
import com.wcpe.integration.WebhookDeliveryService.WebhookDeliveryRequest;
import com.wcpe.integration.WebhookDeliveryService.WebhookDeliveryResponse;
import com.wcpe.integration.WebhookDeliveryService.WebhookDeliveryResult;
import com.wcpe.integration.WebhookDeliveryService.WebhookDeliveryStatus;
import com.wcpe.integration.WebhookDeliveryService.WebhookEndpointPolicy;
import com.wcpe.integration.WebhookDeliveryService.WebhookFailureClass;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebhookDeliveryServiceTest {
  private static final String TENANT_ONE = "11111111-1111-1111-1111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-2222-2222-222222222222";
  private static final String SUBSCRIPTION_ID = "sub-los-main";
  private static final String EVENT_TYPE = "pricing.quote.completed.v1";
  private static final String CORRELATION_ID = "corr-PII-16-S04";

  @Test
  void enqueuesAndDeliversSignedPayloadWithAuditOutboxMetricsAndRedaction() {
    MutableClock clock = new MutableClock(Instant.parse("2026-06-04T04:00:00Z"));
    CapturingClient client = new CapturingClient(List.of(success("accepted without raw borrower body")));
    WebhookDeliveryService service = service(clock, client, 3);

    WebhookDeliveryResponse queued = service.enqueue(command("source-event-1", "{\"borrowerName\":\"Ada\",\"quoteId\":\"Q-1\"}")).value().orElseThrow();
    WebhookDeliveryResponse delivered = service.processDue(TENANT_ONE, clock.instant(), 10).get(0);

    assertEquals(WebhookDeliveryStatus.PENDING, queued.status());
    assertEquals(WebhookDeliveryStatus.DELIVERED, delivered.status());
    assertEquals(1, delivered.attemptCount());
    assertTrue(client.requests.get(0).headers().containsKey("X-Integration-Signature"));
    assertTrue(client.requests.get(0).headers().containsKey("X-Integration-Signature-Version"));
    assertNotEquals(client.requests.get(0).payloadBody(), delivered.payloadHash());
    assertEquals(WebhookDeliveryService.DELIVERED_EVENT_TYPE, service.outboxEvents().get(0).eventType());
    assertEquals(WebhookDeliveryService.AUDIT_DELIVERED_ACTION, service.auditRecords().get(0).action());
    assertFalse(service.outboxEvents().get(0).payload().toString().contains("Ada"));
    assertFalse(service.attemptsForDelivery(TENANT_ONE, delivered.id()).get(0).responseBodyHash().contains("borrower"));
    assertEquals(1L, service.metrics().get("webhook_delivery_attempts_total"));
    assertTrue(service.metrics().get("webhook_delivery_latency_ms") > 0L);
  }

  @Test
  void addsCanonicalSignatureHeaderWhenSignerReturnsSignatureWithoutHeader() {
    MutableClock clock = new MutableClock(Instant.parse("2026-06-04T04:00:00Z"));
    CapturingClient client = new CapturingClient(List.of(success("accepted")));
    WebhookDeliveryService service = service(clock, client, 3, signerWithoutSignatureHeader());

    service.enqueue(command("source-event-signature-header", "{\"quoteId\":\"Q-SIG\"}"));
    service.processDue(TENANT_ONE, clock.instant(), 10);

    assertEquals("signed-vault://tenant-one/webhooks/los-main-2026-06-04T04:00:00Z", client.requests.get(0).headers().get(WebhookDeliveryService.SIGNATURE_HEADER));
    assertEquals("corr-PII-16-S04", client.requests.get(0).headers().get("X-Correlation-Id"));
  }

  @Test
  void usesPolicyBackoffScheduleWithDeterministicJitter() {
    MutableClock firstClock = new MutableClock(Instant.parse("2026-06-04T04:00:00Z"));
    MutableClock secondClock = new MutableClock(Instant.parse("2026-06-04T04:00:00Z"));
    WebhookEndpointPolicy policy =
        new WebhookEndpointPolicy(
            "https://hooks.example.com/wcpe",
            "vault://tenant-one/webhooks/los-main",
            "secret-version-1",
            3,
            List.of(EVENT_TYPE),
            "subscription-source-event",
            List.of(Duration.ofMinutes(2), Duration.ofMinutes(4)),
            Duration.ofSeconds(30));
    WebhookDeliveryService firstService = service(firstClock, new CapturingClient(List.of(serverError())), policy, signer());
    WebhookDeliveryService secondService = service(secondClock, new CapturingClient(List.of(serverError())), policy, signer());

    firstService.enqueue(command("source-event-jitter", "{\"quoteId\":\"Q-JITTER\"}"));
    secondService.enqueue(command("source-event-jitter", "{\"quoteId\":\"Q-JITTER\"}"));
    WebhookDeliveryResponse firstRetry = firstService.processDue(TENANT_ONE, firstClock.instant(), 10).get(0);
    WebhookDeliveryResponse secondRetry = secondService.processDue(TENANT_ONE, secondClock.instant(), 10).get(0);

    Instant earliest = firstClock.base.plus(Duration.ofMinutes(2));
    Instant latest = firstClock.base.plus(Duration.ofMinutes(2)).plus(Duration.ofSeconds(30));
    assertEquals(firstRetry.nextAttemptAt(), secondRetry.nextAttemptAt());
    assertTrue(!firstRetry.nextAttemptAt().isBefore(earliest));
    assertTrue(!firstRetry.nextAttemptAt().isAfter(latest));
    assertNotEquals(firstClock.base.plus(Duration.ofMinutes(1)), firstRetry.nextAttemptAt());
  }

  @Test
  void schedulesRetryThenDeadLettersRetryableFailuresAfterMaxAttempts() {
    MutableClock clock = new MutableClock(Instant.parse("2026-06-04T04:00:00Z"));
    CapturingClient client = new CapturingClient(List.of(serverError(), serverError()));
    WebhookDeliveryService service = service(clock, client, 2);
    WebhookDeliveryResponse queued = service.enqueue(command("source-event-2", "{\"quoteId\":\"Q-2\"}")).value().orElseThrow();

    WebhookDeliveryResponse retryScheduled = service.processDue(TENANT_ONE, clock.instant(), 10).get(0);
    clock.moveTo(retryScheduled.nextAttemptAt());
    WebhookDeliveryResponse deadLettered = service.processDue(TENANT_ONE, clock.instant(), 10).get(0);

    assertEquals(WebhookDeliveryStatus.RETRY_SCHEDULED, retryScheduled.status());
    assertEquals(clock.base.plus(Duration.ofMinutes(1)), retryScheduled.nextAttemptAt());
    assertEquals(WebhookDeliveryStatus.DEAD_LETTERED, deadLettered.status());
    assertEquals(2, service.attemptsForDelivery(TENANT_ONE, queued.id()).size());
    assertEquals(WebhookDeliveryService.DEAD_LETTERED_EVENT_TYPE, service.outboxEvents().get(1).eventType());
    assertEquals(1L, service.metrics().get("webhook_delivery_dlq_total"));
    assertEquals("true", service.outboxEvents().get(1).payload().get("replayEligibility"));
  }

  @Test
  void failsNonRetryableClientErrorButRetriesAllowedClientStatuses() {
    MutableClock clock = new MutableClock(Instant.parse("2026-06-04T04:00:00Z"));
    CapturingClient notFoundClient = new CapturingClient(List.of(new WebhookDeliveryHttpResponse(404, Duration.ofMillis(8), "not found", WebhookFailureClass.NONE)));
    WebhookDeliveryService notFoundService = service(clock, notFoundClient, 3);
    notFoundService.enqueue(command("source-event-3", "{\"quoteId\":\"Q-3\"}"));

    WebhookDeliveryResponse failed = notFoundService.processDue(TENANT_ONE, clock.instant(), 10).get(0);

    assertEquals(WebhookDeliveryStatus.FAILED, failed.status());
    assertEquals("CLIENT_ERROR", failed.failureClass());
    assertFalse(notFoundService.attemptsForDelivery(TENANT_ONE, failed.id()).get(0).retryable());

    CapturingClient tooManyRequestsClient = new CapturingClient(List.of(new WebhookDeliveryHttpResponse(429, Duration.ofMillis(8), "slow down", WebhookFailureClass.NONE)));
    WebhookDeliveryService retryableService = service(clock, tooManyRequestsClient, 3);
    retryableService.enqueue(command("source-event-4", "{\"quoteId\":\"Q-4\"}"));

    WebhookDeliveryResponse retry = retryableService.processDue(TENANT_ONE, clock.instant(), 10).get(0);

    assertEquals(WebhookDeliveryStatus.RETRY_SCHEDULED, retry.status());
    assertEquals("RETRYABLE_CLIENT_ERROR", retry.failureClass());
    assertTrue(retryableService.attemptsForDelivery(TENANT_ONE, retry.id()).get(0).retryable());
  }

  @Test
  void manualRetryPreservesAttemptHistoryAndTenantIsolation() {
    MutableClock clock = new MutableClock(Instant.parse("2026-06-04T04:00:00Z"));
    CapturingClient client = new CapturingClient(List.of(serverError(), success("ok")));
    WebhookDeliveryService service = service(clock, client, 1);
    WebhookDeliveryResponse queued = service.enqueue(command("source-event-5", "{\"quoteId\":\"Q-5\"}")).value().orElseThrow();
    WebhookDeliveryResponse deadLettered = service.processDue(TENANT_ONE, clock.instant(), 10).get(0);

    WebhookDeliveryResponse retried =
        service.manualRetry(new ManualWebhookRetryCommand(TENANT_ONE, deadLettered.id(), "integration-operator", CORRELATION_ID)).value().orElseThrow();

    assertEquals(WebhookDeliveryStatus.DEAD_LETTERED, deadLettered.status());
    assertEquals(WebhookDeliveryStatus.DELIVERED, retried.status());
    assertEquals(2, service.attemptsForDelivery(TENANT_ONE, queued.id()).size());
    assertTrue(service.attemptsForDelivery(TENANT_ONE, queued.id()).get(1).manual());
    assertFalse(service.fetch(TENANT_TWO, queued.id(), CORRELATION_ID).valid());
  }

  @Test
  void failsClosedForMissingPolicyAndDetectsSourceEventConflict() {
    MutableClock clock = new MutableClock(Instant.parse("2026-06-04T04:00:00Z"));
    WebhookDeliveryService missingPolicy = new WebhookDeliveryService(clock, signer(), new CapturingClient(List.of(success("ok"))));

    assertEquals("POLICY_NOT_SATISFIED", missingPolicy.enqueue(command("source-event-6", "{\"quoteId\":\"Q-6\"}")).error().orElseThrow().reason());

    WebhookDeliveryService service = service(clock, new CapturingClient(List.of(success("ok"))), 3);
    WebhookDeliveryResult<WebhookDeliveryResponse> first = service.enqueue(command("source-event-7", "{\"quoteId\":\"Q-7\"}"));
    WebhookDeliveryResult<WebhookDeliveryResponse> replay = service.enqueue(command("source-event-7", "{\"quoteId\":\"Q-7\"}"));
    WebhookDeliveryResult<WebhookDeliveryResponse> conflict = service.enqueue(command("source-event-7", "{\"quoteId\":\"changed\"}"));

    assertTrue(first.valid());
    assertEquals(first.value().orElseThrow(), replay.value().orElseThrow());
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.error().orElseThrow().reason());
  }

  private WebhookDeliveryService service(MutableClock clock, CapturingClient client, int maxAttempts) {
    return service(clock, client, maxAttempts, signer());
  }

  private WebhookDeliveryService service(MutableClock clock, CapturingClient client, int maxAttempts, WebhookDeliveryService.WebhookSignatureBuilder signatureBuilder) {
    return service(
        clock,
        client,
        new WebhookEndpointPolicy(
            "https://hooks.example.com/wcpe",
            "vault://tenant-one/webhooks/los-main",
            "secret-version-1",
            maxAttempts,
            List.of(EVENT_TYPE),
            "subscription-source-event"),
        signatureBuilder);
  }

  private WebhookDeliveryService service(MutableClock clock, CapturingClient client, WebhookEndpointPolicy policy, WebhookDeliveryService.WebhookSignatureBuilder signatureBuilder) {
    WebhookDeliveryService service = new WebhookDeliveryService(clock, signatureBuilder, client);
    service.configureEndpointPolicy(TENANT_ONE, SUBSCRIPTION_ID, policy);
    return service;
  }

  private WebhookDeliveryService.WebhookSignatureBuilder signer() {
    return (secretRef, timestamp, payloadBody) ->
        new SignedWebhookPayload(
            "signed-" + secretRef + "-" + timestamp,
            Map.of(
                "X-Integration-Signature", "signed-" + secretRef,
                "X-Integration-Signature-Version", "secret-version-1",
                "X-Correlation-Id", CORRELATION_ID));
  }

  private WebhookDeliveryService.WebhookSignatureBuilder signerWithoutSignatureHeader() {
    return (secretRef, timestamp, payloadBody) ->
        new SignedWebhookPayload(
            "signed-" + secretRef + "-" + timestamp,
            Map.of(
                "X-Integration-Signature-Version", "secret-version-1",
                "X-Correlation-Id", CORRELATION_ID));
  }

  private EnqueueWebhookDeliveryCommand command(String sourceEventId, String payloadBody) {
    return new EnqueueWebhookDeliveryCommand(TENANT_ONE, SUBSCRIPTION_ID, sourceEventId, EVENT_TYPE, payloadBody, "integration-worker", CORRELATION_ID);
  }

  private static WebhookDeliveryHttpResponse success(String body) {
    return new WebhookDeliveryHttpResponse(202, Duration.ofMillis(12), body, WebhookFailureClass.NONE);
  }

  private static WebhookDeliveryHttpResponse serverError() {
    return new WebhookDeliveryHttpResponse(503, Duration.ofMillis(25), "service unavailable", WebhookFailureClass.NONE);
  }

  private static final class CapturingClient implements WebhookDeliveryService.WebhookEndpointClient {
    private final List<WebhookDeliveryHttpResponse> responses;
    private final List<WebhookDeliveryRequest> requests = new ArrayList<>();
    private int index;

    private CapturingClient(List<WebhookDeliveryHttpResponse> responses) {
      this.responses = responses;
    }

    @Override
    public WebhookDeliveryHttpResponse deliver(WebhookDeliveryRequest request) {
      requests.add(request);
      WebhookDeliveryHttpResponse response = responses.get(Math.min(index, responses.size() - 1));
      index++;
      return response;
    }
  }

  private static final class MutableClock extends Clock {
    private final Instant base;
    private Instant current;

    private MutableClock(Instant current) {
      this.base = current;
      this.current = current;
    }

    private void moveTo(Instant current) {
      this.current = current;
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return current;
    }
  }
}
