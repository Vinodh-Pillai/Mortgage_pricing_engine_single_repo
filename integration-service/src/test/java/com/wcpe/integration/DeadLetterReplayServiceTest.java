package com.wcpe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.integration.DeadLetterReplayService.DeadLetterResponse;
import com.wcpe.integration.DeadLetterReplayService.DeadLetterResult;
import com.wcpe.integration.DeadLetterReplayService.DeadLetterStatus;
import com.wcpe.integration.DeadLetterReplayService.ReplayAttemptStatus;
import com.wcpe.integration.DeadLetterReplayService.ReplayDeadLetterCommand;
import com.wcpe.integration.DeadLetterReplayService.ReplayEnvelope;
import com.wcpe.integration.DeadLetterReplayService.ReplayHandlerResult;
import com.wcpe.integration.DeadLetterReplayService.ReplayMode;
import com.wcpe.integration.DeadLetterReplayService.ReplayResponse;
import com.wcpe.integration.DeadLetterReplayService.SearchDeadLettersQuery;
import com.wcpe.integration.DeadLetterReplayService.SourceType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeadLetterReplayServiceTest {
  private static final String TENANT_ONE = "11111111-1111-1111-1111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-2222-2222-222222222222";
  private static final String DEAD_LETTER_ID = "33333333-3333-3333-3333-333333333333";
  private static final String CORRELATION_ID = "corr-PII-16-S09";

  @Test
  void recordsSearchesAndDetailsDeadLettersWithRedactedPayloadSummary() {
    CapturingReplayHandler handler = new CapturingReplayHandler(true);
    DeadLetterReplayService service = service(handler);

    DeadLetterResponse opened = service.recordDeadLetter(record(SourceType.WEBHOOK_DELIVERY, DEAD_LETTER_ID, false)).value().orElseThrow();

    assertEquals(DeadLetterStatus.OPEN, opened.status());
    assertEquals("<redacted>", opened.resultSummary().get("payloadRef"));
    assertFalse(opened.resultSummary().toString().contains("payload://tenant-one/webhook/raw-body"));
    assertEquals(1, service.search(new SearchDeadLettersQuery(TENANT_ONE, DeadLetterStatus.OPEN, SourceType.WEBHOOK_DELIVERY, "integration.webhook-delivery.failed.v1")).size());
    assertTrue(service.search(new SearchDeadLettersQuery(TENANT_TWO, DeadLetterStatus.OPEN, SourceType.WEBHOOK_DELIVERY, "integration.webhook-delivery.failed.v1")).isEmpty());
    assertEquals(DeadLetterReplayService.OPENED_EVENT_TYPE, service.outboxEvents().get(0).eventType());
    assertEquals(1L, service.metrics().get("dlq_items_open"));
    assertTrue(handler.envelopes.isEmpty());
  }

  @Test
  void replaysSingleItemWithIdempotencyAuditOutboxAndNoPayloadLeakage() {
    CapturingReplayHandler handler = new CapturingReplayHandler(true);
    DeadLetterReplayService service = service(handler);
    service.recordDeadLetter(record(SourceType.WEBHOOK_DELIVERY, DEAD_LETTER_ID, false));

    ReplayResponse replayed = service.replay(replay(DEAD_LETTER_ID, ReplayMode.ORIGINAL_CONFIG, 3, "idem-replay-1")).value().orElseThrow();
    ReplayResponse idempotent = service.replay(replay(DEAD_LETTER_ID, ReplayMode.ORIGINAL_CONFIG, 3, "idem-replay-1")).value().orElseThrow();
    DeadLetterResult<ReplayResponse> conflict = service.replay(replay(DEAD_LETTER_ID, ReplayMode.ORIGINAL_CONFIG, 4, "idem-replay-1"));

    assertEquals(ReplayAttemptStatus.REPLAYED, replayed.attemptStatus());
    assertEquals(DeadLetterStatus.REPLAYED, replayed.status());
    assertEquals(replayed, idempotent);
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.error().orElseThrow().reason());
    assertEquals(1, handler.envelopes.size());
    assertEquals(ReplayMode.ORIGINAL_CONFIG, handler.envelopes.get(0).mode());
    assertEquals(DeadLetterReplayService.REPLAY_REQUESTED_EVENT_TYPE, service.outboxEvents().get(1).eventType());
    assertEquals(DeadLetterReplayService.REPLAYED_EVENT_TYPE, service.outboxEvents().get(2).eventType());
    assertEquals(DeadLetterReplayService.AUDIT_REPLAY_ACTION, service.auditRecords().get(0).action());
    assertFalse(service.outboxEvents().toString().contains("borrowerName"));
    assertEquals(1L, service.metrics().get("dlq_replay_success_total"));
  }

  @Test
  void dryRunValidatesReplayWithoutChangingItemStatusOrRepublishing() {
    CapturingReplayHandler handler = new CapturingReplayHandler(true);
    DeadLetterReplayService service = service(handler);
    service.recordDeadLetter(record(SourceType.INVESTOR_API, DEAD_LETTER_ID, false));

    ReplayResponse dryRun = service.replay(replay(DEAD_LETTER_ID, ReplayMode.DRY_RUN, 3, "idem-dry-run")).value().orElseThrow();

    assertEquals(ReplayAttemptStatus.DRY_RUN_PASSED, dryRun.attemptStatus());
    assertEquals(DeadLetterStatus.OPEN, dryRun.status());
    assertEquals(1, handler.envelopes.size());
    assertEquals(ReplayMode.DRY_RUN, handler.envelopes.get(0).mode());
    assertEquals(1, service.outboxEvents().size());
  }

  @Test
  void latestCompatibleReplayRequiresRegisteredSchemaCompatibility() {
    CapturingReplayHandler handler = new CapturingReplayHandler(true);
    DeadLetterReplayService service = service(handler);
    service.recordDeadLetter(record(SourceType.INVESTOR_API, DEAD_LETTER_ID, false));

    ReplayResponse blocked = service.replay(replay(DEAD_LETTER_ID, ReplayMode.LATEST_COMPATIBLE_CONFIG, 4, "idem-latest-blocked")).value().orElseThrow();
    service.allowLatestCompatibleSchema(SourceType.INVESTOR_API, "investor.feed.failed.v1", 3, 4);
    ReplayResponse replayed = service.replay(replay(DEAD_LETTER_ID, ReplayMode.LATEST_COMPATIBLE_CONFIG, 4, "idem-latest-replayed")).value().orElseThrow();

    assertEquals(ReplayAttemptStatus.BLOCKED, blocked.attemptStatus());
    assertEquals("SCHEMA_COMPATIBILITY_NOT_PROVEN", blocked.resultSummary().get("reason"));
    assertEquals(ReplayAttemptStatus.REPLAYED, replayed.attemptStatus());
    assertEquals(ReplayMode.LATEST_COMPATIBLE_CONFIG, handler.envelopes.get(0).mode());
  }

  @Test
  void blocksReplayForMissingHandlerLegalHoldForbiddenPayloadAndStaleSchema() {
    DeadLetterReplayService missingHandler = new DeadLetterReplayService(Clock.fixed(Instant.parse("2026-06-07T09:00:00Z"), ZoneOffset.UTC));
    missingHandler.recordDeadLetter(record(SourceType.OUTBOX, DEAD_LETTER_ID, false));
    ReplayResponse noHandler = missingHandler.replay(replay(DEAD_LETTER_ID, ReplayMode.ORIGINAL_CONFIG, 3, "idem-no-handler")).value().orElseThrow();
    assertEquals(ReplayAttemptStatus.BLOCKED, noHandler.attemptStatus());
    assertEquals("DEPENDENCY_UNAVAILABLE", noHandler.resultSummary().get("reason"));

    DeadLetterReplayService service = service(new CapturingReplayHandler(true));
    service.recordDeadLetter(record(SourceType.WEBHOOK_DELIVERY, DEAD_LETTER_ID, true));
    ReplayResponse legalHold = service.replay(replay(DEAD_LETTER_ID, ReplayMode.ORIGINAL_CONFIG, 3, "idem-legal-hold")).value().orElseThrow();
    assertEquals("LEGAL_HOLD_CONFLICT", legalHold.resultSummary().get("reason"));

    String secretId = "44444444-4444-4444-4444-444444444444";
    service.recordDeadLetter(recordWithPayloadRef(SourceType.WEBHOOK_DELIVERY, secretId, "secret://vault/raw-secret", false));
    ReplayResponse secretBlocked = service.replay(replay(secretId, ReplayMode.ORIGINAL_CONFIG, 3, "idem-secret")).value().orElseThrow();
    assertEquals("FORBIDDEN_PAYLOAD_MATERIAL", secretBlocked.resultSummary().get("reason"));

    String staleId = "55555555-5555-5555-5555-555555555555";
    service.recordDeadLetter(record(SourceType.WEBHOOK_DELIVERY, staleId, false));
    ReplayResponse stale = service.replay(replay(staleId, ReplayMode.ORIGINAL_CONFIG, 99, "idem-stale")).value().orElseThrow();
    assertEquals("STALE_SCHEMA_OR_CONFIG", stale.resultSummary().get("reason"));
    assertEquals(3L, service.metrics().get("dlq_replay_blocked_total"));
  }

  @Test
  void bulkReplayUsesCallerBoundedMaxItemsAndDiscardAuditsReason() {
    CapturingReplayHandler handler = new CapturingReplayHandler(true);
    DeadLetterReplayService service = service(handler);
    service.recordDeadLetter(record(SourceType.SFTP_FEED, DEAD_LETTER_ID, false));
    service.recordDeadLetter(record(SourceType.SFTP_FEED, "66666666-6666-6666-6666-666666666666", false));

    List<ReplayResponse> bulk = service.bulkReplay(new DeadLetterReplayService.BulkReplayCommand(TENANT_ONE, SourceType.SFTP_FEED, "sftp.feed.failed.v1", ReplayMode.ORIGINAL_CONFIG, "operator requested safe replay", 1, "ops-admin", "bulk-idem", CORRELATION_ID));

    assertEquals(1, bulk.size());
    assertEquals(1, handler.envelopes.size());

    ReplayResponse discarded = service.discard(new DeadLetterReplayService.DiscardDeadLetterCommand(TENANT_ONE, "66666666-6666-6666-6666-666666666666", "duplicate downstream recovery", "ops-admin", "discard-idem", CORRELATION_ID)).value().orElseThrow();

    assertEquals(DeadLetterStatus.DISCARDED, discarded.status());
    assertEquals(DeadLetterReplayService.DISCARDED_EVENT_TYPE, service.outboxEvents().get(service.outboxEvents().size() - 1).eventType());
    assertEquals(DeadLetterReplayService.AUDIT_DISCARD_ACTION, service.auditRecords().get(service.auditRecords().size() - 1).action());
  }

  private DeadLetterReplayService service(CapturingReplayHandler handler) {
    DeadLetterReplayService service = new DeadLetterReplayService(Clock.fixed(Instant.parse("2026-06-07T09:00:00Z"), ZoneOffset.UTC));
    for (SourceType sourceType : SourceType.values()) {
      service.registerHandler(sourceType, handler);
    }
    return service;
  }

  private DeadLetterReplayService.RecordDeadLetterCommand record(SourceType sourceType, String id, boolean legalHold) {
    String eventType = switch (sourceType) {
      case WEBHOOK_DELIVERY -> "integration.webhook-delivery.failed.v1";
      case INVESTOR_API -> "investor.feed.failed.v1";
      case SFTP_FEED -> "sftp.feed.failed.v1";
      case OUTBOX -> "integration.outbox.failed.v1";
      case LOS_QUOTE_EVENT -> "los.quote.failed.v1";
    };
    return recordWithPayloadRef(sourceType, id, "payload://tenant-one/webhook/raw-body", legalHold, eventType);
  }

  private DeadLetterReplayService.RecordDeadLetterCommand recordWithPayloadRef(SourceType sourceType, String id, String payloadRef, boolean legalHold) {
    return recordWithPayloadRef(sourceType, id, payloadRef, legalHold, "integration.webhook-delivery.failed.v1");
  }

  private DeadLetterReplayService.RecordDeadLetterCommand recordWithPayloadRef(SourceType sourceType, String id, String payloadRef, boolean legalHold, String eventType) {
    return new DeadLetterReplayService.RecordDeadLetterCommand(TENANT_ONE, id, sourceType, "source-1", eventType, 3, "config://tenant-one/integrations/v3", payloadRef, "hash-only-no-borrower-pii", "RETRYABLE_5XX", legalHold, "ops-admin", CORRELATION_ID);
  }

  private ReplayDeadLetterCommand replay(String id, ReplayMode mode, int expectedSchemaVersion, String idempotencyKey) {
    return new ReplayDeadLetterCommand(TENANT_ONE, id, mode, "operator supplied audit reason", expectedSchemaVersion, "ops-admin", idempotencyKey, CORRELATION_ID);
  }

  private static final class CapturingReplayHandler implements DeadLetterReplayService.ReplayHandler {
    private final boolean success;
    private final List<ReplayEnvelope> envelopes = new ArrayList<>();

    private CapturingReplayHandler(boolean success) {
      this.success = success;
    }

    @Override
    public ReplayHandlerResult replay(ReplayEnvelope envelope) {
      envelopes.add(envelope);
      return new ReplayHandlerResult(success, success ? "REPLAY_ACCEPTED" : "HANDLER_BLOCKED", success ? "replayed to source handler" : "handler blocked replay", Map.of("sourceType", envelope.sourceType().name(), "payloadHash", envelope.payloadHash()));
    }
  }
}
