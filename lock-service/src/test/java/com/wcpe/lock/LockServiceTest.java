package com.wcpe.lock;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LockServiceTest {
  private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private LockService service;

  @BeforeEach
  void setUp() {
    service = new LockService();
  }

  @Test
  void rateLockRequestCommandCreatesRequestedStateWithAuditAndOutbox() {
    LockModels.LockRequestResponse response = service.requestLock(validCommand("REQ-001", "IDEMP-001", TENANT_A));

    assertEquals(LockModels.RateLockStatus.REQUESTED, response.status());
    assertEquals("lock.requested.v1", response.outboxEventType());
    assertTrue(response.auditRef().startsWith("AUDIT-LOCK-"));
    assertTrue(response.replayRef().startsWith("REPLAY-LOCK-"));
    assertEquals(1, service.outboxEvents().size());
    assertEquals(1, service.auditSnapshots().size());
    assertEquals(1, service.metrics().lockRequestTotal());
  }

  @Test
  void createsPendingApprovalWhenPolicyDoesNotPermitAutoApproval() {
    LockModels.LockRequestCommand command = validCommand("REQ-002", "IDEMP-002", TENANT_A);
    command = new LockModels.LockRequestCommand(
      command.tenantId(), command.requestId(), command.actorId(), command.quoteId(), command.loanId(),
      command.scenarioHash(), command.pricingResultHash(), command.rateSheetVersion(), command.productId(),
      command.investorId(), command.channel(), command.lockPeriodDays(), command.quotePricedAt(),
      command.requestedAt(), command.idempotencyKey(), command.correlationId(), command.permissionGranted(),
      false, command.quoteFresh(), command.scenarioHashUnchanged(), command.pricingHashUnchanged(),
      command.rateSheetLockable(), command.marketSuspended(), command.investorSuspended(),
      command.complianceBlocking(), command.tenantChannelConfigPresent(), command.investorAmbiguous(),
      command.lockPolicyVersionId(), command.complianceEvidenceRef(), command.sourceRefs()
    );

    assertEquals(LockModels.RateLockStatus.PENDING_APPROVAL, service.requestLock(command).status());
  }

  @Test
  void rejectsStaleQuoteFailClosedWithoutCommittedLock() {
    LockModels.LockRequestCommand command = withQuoteFresh(validCommand("REQ-003", "IDEMP-003", TENANT_A), false);

    LockServiceException error = assertThrows(LockServiceException.class, () -> service.requestLock(command));

    assertEquals("POLICY_NOT_SATISFIED", error.code());
    assertTrue(error.getMessage().contains("quote snapshot is stale"));
    assertEquals(0, service.committedLockCount());
    assertEquals(1, service.metrics().lockRequestRejectedTotal());
  }

  @Test
  void replaysSameIdempotencyKeyAndRejectsConflictingPayload() {
    LockModels.LockRequestCommand command = validCommand("REQ-004", "IDEMP-004", TENANT_A);

    LockModels.LockRequestResponse first = service.requestLockReplayAware(command);
    LockModels.LockRequestResponse replay = service.requestLockReplayAware(command);

    assertEquals(first.lockId(), replay.lockId());
    LockModels.LockRequestCommand conflict = validCommand("REQ-004-CHANGED", "IDEMP-004", TENANT_A);
    LockServiceException error = assertThrows(LockServiceException.class, () -> service.requestLock(conflict));
    assertEquals("IDEMPOTENCY_CONFLICT", error.code());
  }

  @Test
  void duplicateActiveQuoteIsRejectedPerTenantOnly() {
    service.requestLock(validCommand("REQ-005", "IDEMP-005", TENANT_A));

    LockServiceException duplicate = assertThrows(
      LockServiceException.class,
      () -> service.requestLock(validCommand("REQ-006", "IDEMP-006", TENANT_A))
    );
    assertEquals("DUPLICATE_ACTIVE_QUOTE_LOCK", duplicate.code());

    LockModels.LockRequestResponse otherTenant = service.requestLock(validCommand("REQ-007", "IDEMP-007", TENANT_B));
    assertEquals(TENANT_B, otherTenant.tenantId());
  }

  @Test
  void tenantIsolationPreventsCrossTenantRead() {
    LockModels.LockRequestResponse response = service.requestLock(validCommand("REQ-008", "IDEMP-008", TENANT_A));

    LockServiceException error = assertThrows(
      LockServiceException.class,
      () -> service.getLock(TENANT_B, response.lockId())
    );

    assertEquals("NOT_FOUND", error.code());
  }

  @Test
  void invalidTransitionReturnsLockStateConflictWithoutSideEffects() {
    LockModels.LockRequestResponse response = service.requestLock(validCommand("REQ-009", "IDEMP-009", TENANT_A));

    LockServiceException error = assertThrows(
      LockServiceException.class,
      () -> service.transition(TENANT_A, response.lockId(), LockModels.RateLockStatus.REQUESTED)
    );

    assertEquals("LOCK_STATE_CONFLICT", error.code());
    assertEquals(1, service.getLock(TENANT_A, response.lockId()).version());
  }

  @Test
  void approveRequestedLockRecordsDecisionAuditOutboxAndMetrics() {
    LockModels.LockRequestResponse request = service.requestLock(validCommand("REQ-010", "IDEMP-010", TENANT_A));

    LockModels.LockDecisionResponse decision = service.decideLock(decisionCommand(
      request.lockId(), LockModels.LockDecisionType.APPROVE, request.version(), "IDEMP-DEC-010", TENANT_A
    ));

    assertEquals(LockModels.LockDecisionType.APPROVE, decision.decision());
    assertEquals(LockModels.RateLockStatus.REQUESTED, decision.previousStatus());
    assertEquals(LockModels.RateLockStatus.APPROVED, decision.status());
    assertEquals(2, decision.version());
    assertEquals("lock.approved.v1", decision.outboxEventType());
    assertEquals("LOCK_APPROVED", service.auditSnapshots().get(1).action());
    assertEquals("lock.approved.v1", service.outboxEvents().get(1).eventType());
    assertEquals(1, service.metrics().lockApprovalTotal());
  }

  @Test
  void rejectPendingApprovalRequiresConfiguredReasonCodeAndReleasesQuote() {
    LockModels.LockRequestCommand command = withAutoApproval(validCommand("REQ-011", "IDEMP-011", TENANT_A), false);
    LockModels.LockRequestResponse request = service.requestLock(command);

    LockServiceException missingReason = assertThrows(
      LockServiceException.class,
      () -> service.decideLock(decisionCommand(request.lockId(), LockModels.LockDecisionType.REJECT, request.version(), "IDEMP-DEC-011A", TENANT_A, List.of()))
    );
    assertEquals("VALIDATION_FAILED", missingReason.code());

    LockModels.LockDecisionResponse decision = service.decideLock(decisionCommand(
      request.lockId(), LockModels.LockDecisionType.REJECT, request.version(), "IDEMP-DEC-011B", TENANT_A
    ));

    assertEquals(LockModels.RateLockStatus.REJECTED, decision.status());
    assertEquals("lock.rejected.v1", decision.outboxEventType());
    assertEquals(1, service.metrics().lockRejectionTotal());
    LockModels.LockRequestResponse replacement = service.requestLock(validCommand("REQ-011-R", "IDEMP-011-R", TENANT_A));
    assertEquals(LockModels.RateLockStatus.REQUESTED, replacement.status());
  }

  @Test
  void decisionFailsOnStaleAggregateVersion() {
    LockModels.LockRequestResponse request = service.requestLock(validCommand("REQ-012", "IDEMP-012", TENANT_A));

    LockServiceException error = assertThrows(
      LockServiceException.class,
      () -> service.decideLock(decisionCommand(request.lockId(), LockModels.LockDecisionType.APPROVE, 99, "IDEMP-DEC-012", TENANT_A))
    );

    assertEquals("VERSION_CONFLICT", error.code());
    assertEquals(1, service.getLock(TENANT_A, request.lockId()).version());
  }

  @Test
  void separationOfDutiesBlocksRequesterApproval() {
    LockModels.LockRequestResponse request = service.requestLock(validCommand("REQ-013", "IDEMP-013", TENANT_A));

    LockServiceException error = assertThrows(
      LockServiceException.class,
      () -> service.decideLock(decisionCommand(
        request.lockId(), LockModels.LockDecisionType.APPROVE, request.version(), "IDEMP-DEC-013", TENANT_A,
        List.of("APPROVAL_POLICY_OK"), "lock-desk-user-1"
      ))
    );

    assertEquals("SEPARATION_OF_DUTIES_VIOLATION", error.code());
  }

  @Test
  void lockDecisionReplayIsIdempotentAndRejectsConflictingPayload() {
    LockModels.LockRequestResponse request = service.requestLock(validCommand("REQ-014", "IDEMP-014", TENANT_A));
    LockModels.LockDecisionCommand command = decisionCommand(
      request.lockId(), LockModels.LockDecisionType.APPROVE, request.version(), "IDEMP-DEC-014", TENANT_A
    );

    LockModels.LockDecisionResponse first = service.decideLockReplayAware(command);
    LockModels.LockDecisionResponse replay = service.decideLockReplayAware(command);

    assertEquals(first.decisionId(), replay.decisionId());
    LockServiceException conflict = assertThrows(
      LockServiceException.class,
      () -> service.decideLock(decisionCommand(request.lockId(), LockModels.LockDecisionType.REJECT, request.version(), "IDEMP-DEC-014", TENANT_A))
    );
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.code());
  }

  @Test
  void decisionTenantIsolationPreventsCrossTenantApproval() {
    LockModels.LockRequestResponse request = service.requestLock(validCommand("REQ-015", "IDEMP-015", TENANT_A));

    LockServiceException error = assertThrows(
      LockServiceException.class,
      () -> service.decideLock(decisionCommand(request.lockId(), LockModels.LockDecisionType.APPROVE, request.version(), "IDEMP-DEC-015", TENANT_B))
    );

    assertEquals("NOT_FOUND", error.code());
  }

  @Test
  void decisionPolicyBlocksApprovalFailClosed() {
    LockModels.LockRequestResponse request = service.requestLock(validCommand("REQ-016", "IDEMP-016", TENANT_A));
    LockModels.LockDecisionCommand command = withDecisionPolicyCurrent(
      decisionCommand(request.lockId(), LockModels.LockDecisionType.APPROVE, request.version(), "IDEMP-DEC-016", TENANT_A),
      false
    );

    LockServiceException error = assertThrows(LockServiceException.class, () -> service.decideLock(command));

    assertEquals("LOCK_DECISION_BLOCKED_BY_POLICY", error.code());
    assertEquals(LockModels.RateLockStatus.REQUESTED, service.getLock(TENANT_A, request.lockId()).status());
    assertEquals(1, service.metrics().lockDecisionPolicyBlockedTotal());
  }

  @Test
  void FreshnessAllowsWithinConfiguredWindowTest() {
    LockModels.FreshnessCheckResponse response = service.checkFreshness(freshnessCommand("FRESH-REQ-001", "FRESH-IDEMP-001", TENANT_A));

    assertEquals(LockModels.FreshnessDecisionType.FRESH, response.decision());
    assertTrue(response.reasonCodes().contains("QUOTE_FRESH"));
    assertEquals(300, response.quoteAgeSeconds());
    assertTrue(response.replayRef().startsWith("REPLAY-FRESHNESS-"));
    assertEquals(1, service.committedFreshnessCheckCount());
    assertEquals(1, service.metrics().freshnessCheckTotal());
  }

  @Test
  void FreshnessBlocksScenarioHashMismatchTest() {
    LockModels.FreshnessCheckCommand command = withCurrentScenarioHash(
      freshnessCommand("FRESH-REQ-002", "FRESH-IDEMP-002", TENANT_A),
      "scenario-hash-v2"
    );

    LockModels.FreshnessCheckResponse response = service.checkFreshness(command);

    assertEquals(LockModels.FreshnessDecisionType.STALE, response.decision());
    assertTrue(response.reasonCodes().contains("SCENARIO_HASH_CHANGED"));
    assertFalse(response.decision().lockable());
  }

  @Test
  void FreshnessBlocksMarketSuspensionTest() {
    LockModels.FreshnessCheckCommand command = withMarketSuspended(
      freshnessCommand("FRESH-REQ-003", "FRESH-IDEMP-003", TENANT_A),
      true
    );

    LockModels.FreshnessCheckResponse response = service.checkFreshness(command);

    assertEquals(LockModels.FreshnessDecisionType.POLICY_SUSPENDED, response.decision());
    assertTrue(response.reasonCodes().contains("MARKET_SUSPENDED"));
  }

  @Test
  void FreshnessPolicyAmbiguityFailsClosedTest() {
    LockModels.FreshnessCheckCommand command = withPolicyState(
      freshnessCommand("FRESH-REQ-004", "FRESH-IDEMP-004", TENANT_A),
      true,
      true
    );

    LockModels.FreshnessCheckResponse response = service.checkFreshness(command);

    assertEquals(LockModels.FreshnessDecisionType.CONFIG_ERROR, response.decision());
    assertTrue(response.reasonCodes().contains("CONFIG_AMBIGUOUS"));
    assertEquals(1, service.metrics().freshnessPolicyResolutionFailureTotal());
  }

  @Test
  void freshnessCheckPersistsReplayHashAuditAndOptionalOutbox() {
    LockModels.FreshnessCheckResponse response = service.checkFreshness(freshnessCommand("FRESH-REQ-005", "FRESH-IDEMP-005", TENANT_A));

    LockModels.FreshnessCheckRecord record = service.getFreshnessCheck(TENANT_A, response.checkId());

    assertEquals(response.resultHash(), record.resultHash());
    assertEquals(response.decision(), record.decision());
    assertEquals("lock.freshness_checked.v1", service.outboxEvents().get(0).eventType());
    assertEquals("LOCK_FRESHNESS_CHECKED", service.auditSnapshots().get(0).action());
  }

  @Test
  void freshnessTenantPolicyIsolationAndIdempotency() {
    LockModels.FreshnessCheckCommand command = freshnessCommand("FRESH-REQ-006", "FRESH-IDEMP-006", TENANT_A);

    LockModels.FreshnessCheckResponse first = service.checkFreshnessReplayAware(command);
    LockModels.FreshnessCheckResponse replay = service.checkFreshnessReplayAware(command);

    assertEquals(first.checkId(), replay.checkId());
    assertThrows(LockServiceException.class, () -> service.getFreshnessCheck(TENANT_B, first.checkId()));
    LockServiceException conflict = assertThrows(
      LockServiceException.class,
      () -> service.checkFreshness(freshnessCommand("FRESH-REQ-006-CHANGED", "FRESH-IDEMP-006", TENANT_A))
    );
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.code());
  }

  @Test
  void freshnessExpiresSoonUsesConfiguredPolicyWindow() {
    LockModels.FreshnessCheckCommand command = new LockModels.FreshnessCheckCommand(
      TENANT_A,
      "FRESH-REQ-007",
      "loan-officer-7",
      "QUOTE-FRESHNESS",
      "scenario-hash-v1",
      "pricing-hash-v1",
      "scenario-hash-v1",
      "pricing-hash-v1",
      "rate-sheet-v1",
      "market-data-v1",
      "CONVENTIONAL-30Y",
      "INVESTOR-A",
      "retail",
      Instant.parse("2026-06-04T20:55:00Z"),
      Instant.parse("2026-06-04T21:00:00Z"),
      360,
      90,
      Instant.parse("2026-06-04T21:01:00Z"),
      "freshness-policy-v1",
      "compliance-evidence-v1",
      true,
      true,
      false,
      true,
      false,
      true,
      false,
      false,
      "FRESH-IDEMP-007",
      "corr-pii10-s03",
      Map.of("quoteSnapshot", "quote-snapshot-v1", "policyConfig", "freshness-policy-v1")
    );

    LockModels.FreshnessCheckResponse response = service.checkFreshness(command);

    assertEquals(LockModels.FreshnessDecisionType.EXPIRES_SOON, response.decision());
    assertTrue(response.reasonCodes().contains("QUOTE_EXPIRES_SOON"));
    assertEquals(1, service.metrics().freshnessExpiresSoonTotal());
  }

  private static LockModels.LockRequestCommand validCommand(String requestId, String idempotencyKey, UUID tenantId) {
    return new LockModels.LockRequestCommand(
      tenantId,
      requestId,
      "loan-officer-7",
      "QUOTE-PII10",
      "LOAN-PII10",
      "scenario-hash-v1",
      "pricing-hash-v1",
      "rate-sheet-v1",
      "CONVENTIONAL-30Y",
      "INVESTOR-A",
      "retail",
      30,
      Instant.parse("2026-06-04T20:55:00Z"),
      Instant.parse("2026-06-04T21:00:00Z"),
      idempotencyKey,
      "corr-pii10",
      true,
      true,
      true,
      true,
      true,
      true,
      false,
      false,
      false,
      true,
      false,
      "lock-policy-v1",
      "compliance-evidence-v1",
      Map.of("quoteSnapshot", "quote-snapshot-v1", "pricingSnapshot", "pricing-snapshot-v1")
    );
  }

  private static LockModels.FreshnessCheckCommand freshnessCommand(String requestId, String idempotencyKey, UUID tenantId) {
    return new LockModels.FreshnessCheckCommand(
      tenantId,
      requestId,
      "loan-officer-7",
      "QUOTE-FRESHNESS",
      "scenario-hash-v1",
      "pricing-hash-v1",
      "scenario-hash-v1",
      "pricing-hash-v1",
      "rate-sheet-v1",
      "market-data-v1",
      "CONVENTIONAL-30Y",
      "INVESTOR-A",
      "retail",
      Instant.parse("2026-06-04T20:55:00Z"),
      Instant.parse("2026-06-04T21:00:00Z"),
      900,
      120,
      Instant.parse("2026-06-04T21:10:00Z"),
      "freshness-policy-v1",
      "compliance-evidence-v1",
      true,
      true,
      false,
      true,
      false,
      true,
      false,
      true,
      idempotencyKey,
      "corr-pii10-s03",
      Map.of("quoteSnapshot", "quote-snapshot-v1", "policyConfig", "freshness-policy-v1")
    );
  }

  private static LockModels.FreshnessCheckCommand withCurrentScenarioHash(LockModels.FreshnessCheckCommand command, String currentScenarioHash) {
    return new LockModels.FreshnessCheckCommand(
      command.tenantId(), command.requestId(), command.actorId(), command.quoteId(), command.scenarioHash(),
      command.pricingResultHash(), currentScenarioHash, command.currentPricingResultHash(), command.rateSheetVersion(),
      command.marketDataVersion(), command.productId(), command.investorId(), command.channel(), command.quotePricedAt(),
      command.evaluatedAt(), command.maxQuoteAgeSeconds(), command.expirySoonWindowSeconds(), command.expiresAt(),
      command.policyVersionId(), command.complianceEvidenceRef(), command.permissionGranted(), command.policyResolved(),
      command.policyAmbiguous(), command.rateSheetLockable(), command.marketSuspended(), command.investorEnabled(),
      command.complianceBlocking(), command.emitAuditEvent(), command.idempotencyKey(), command.correlationId(), command.sourceRefs()
    );
  }

  private static LockModels.FreshnessCheckCommand withMarketSuspended(LockModels.FreshnessCheckCommand command, boolean marketSuspended) {
    return new LockModels.FreshnessCheckCommand(
      command.tenantId(), command.requestId(), command.actorId(), command.quoteId(), command.scenarioHash(),
      command.pricingResultHash(), command.currentScenarioHash(), command.currentPricingResultHash(), command.rateSheetVersion(),
      command.marketDataVersion(), command.productId(), command.investorId(), command.channel(), command.quotePricedAt(),
      command.evaluatedAt(), command.maxQuoteAgeSeconds(), command.expirySoonWindowSeconds(), command.expiresAt(),
      command.policyVersionId(), command.complianceEvidenceRef(), command.permissionGranted(), command.policyResolved(),
      command.policyAmbiguous(), command.rateSheetLockable(), marketSuspended, command.investorEnabled(),
      command.complianceBlocking(), command.emitAuditEvent(), command.idempotencyKey(), command.correlationId(), command.sourceRefs()
    );
  }

  private static LockModels.FreshnessCheckCommand withPolicyState(
    LockModels.FreshnessCheckCommand command,
    boolean policyResolved,
    boolean policyAmbiguous
  ) {
    return new LockModels.FreshnessCheckCommand(
      command.tenantId(), command.requestId(), command.actorId(), command.quoteId(), command.scenarioHash(),
      command.pricingResultHash(), command.currentScenarioHash(), command.currentPricingResultHash(), command.rateSheetVersion(),
      command.marketDataVersion(), command.productId(), command.investorId(), command.channel(), command.quotePricedAt(),
      command.evaluatedAt(), command.maxQuoteAgeSeconds(), command.expirySoonWindowSeconds(), command.expiresAt(),
      command.policyVersionId(), command.complianceEvidenceRef(), command.permissionGranted(), policyResolved,
      policyAmbiguous, command.rateSheetLockable(), command.marketSuspended(), command.investorEnabled(),
      command.complianceBlocking(), command.emitAuditEvent(), command.idempotencyKey(), command.correlationId(), command.sourceRefs()
    );
  }

  private static LockModels.LockRequestCommand withQuoteFresh(LockModels.LockRequestCommand command, boolean quoteFresh) {
    return new LockModels.LockRequestCommand(
      command.tenantId(), command.requestId(), command.actorId(), command.quoteId(), command.loanId(),
      command.scenarioHash(), command.pricingResultHash(), command.rateSheetVersion(), command.productId(),
      command.investorId(), command.channel(), command.lockPeriodDays(), command.quotePricedAt(),
      command.requestedAt(), command.idempotencyKey(), command.correlationId(), command.permissionGranted(),
      command.autoApprovalPermitted(), quoteFresh, command.scenarioHashUnchanged(), command.pricingHashUnchanged(),
      command.rateSheetLockable(), command.marketSuspended(), command.investorSuspended(),
      command.complianceBlocking(), command.tenantChannelConfigPresent(), command.investorAmbiguous(),
      command.lockPolicyVersionId(), command.complianceEvidenceRef(), command.sourceRefs()
    );
  }

  private static LockModels.LockRequestCommand withAutoApproval(LockModels.LockRequestCommand command, boolean autoApprovalPermitted) {
    return new LockModels.LockRequestCommand(
      command.tenantId(), command.requestId(), command.actorId(), command.quoteId(), command.loanId(),
      command.scenarioHash(), command.pricingResultHash(), command.rateSheetVersion(), command.productId(),
      command.investorId(), command.channel(), command.lockPeriodDays(), command.quotePricedAt(),
      command.requestedAt(), command.idempotencyKey(), command.correlationId(), command.permissionGranted(),
      autoApprovalPermitted, command.quoteFresh(), command.scenarioHashUnchanged(), command.pricingHashUnchanged(),
      command.rateSheetLockable(), command.marketSuspended(), command.investorSuspended(),
      command.complianceBlocking(), command.tenantChannelConfigPresent(), command.investorAmbiguous(),
      command.lockPolicyVersionId(), command.complianceEvidenceRef(), command.sourceRefs()
    );
  }

  private static LockModels.LockDecisionCommand decisionCommand(
    String lockId,
    LockModels.LockDecisionType decision,
    int expectedVersion,
    String idempotencyKey,
    UUID tenantId
  ) {
    return decisionCommand(lockId, decision, expectedVersion, idempotencyKey, tenantId, List.of("TENANT_REASON_OK"));
  }

  private static LockModels.LockDecisionCommand decisionCommand(
    String lockId,
    LockModels.LockDecisionType decision,
    int expectedVersion,
    String idempotencyKey,
    UUID tenantId,
    List<String> reasonCodes
  ) {
    return decisionCommand(lockId, decision, expectedVersion, idempotencyKey, tenantId, reasonCodes, "requester-9");
  }

  private static LockModels.LockDecisionCommand decisionCommand(
    String lockId,
    LockModels.LockDecisionType decision,
    int expectedVersion,
    String idempotencyKey,
    UUID tenantId,
    List<String> reasonCodes,
    String requesterActorId
  ) {
    return new LockModels.LockDecisionCommand(
      tenantId,
      lockId,
      decision,
      "lock-desk-user-1",
      requesterActorId,
      expectedVersion,
      reasonCodes,
      "Decision note is retained inside the service only",
      "lock-policy-v1",
      "compliance-evidence-v1",
      idempotencyKey,
      "corr-pii10-s02",
      Instant.parse("2026-06-04T21:05:00Z"),
      true,
      true,
      true
    );
  }

  private static LockModels.LockDecisionCommand withDecisionPolicyCurrent(LockModels.LockDecisionCommand command, boolean decisionPolicyCurrent) {
    return new LockModels.LockDecisionCommand(
      command.tenantId(), command.lockId(), command.decision(), command.actorId(), command.requesterActorId(),
      command.expectedVersion(), command.reasonCodes(), command.note(), command.policyVersionId(),
      command.complianceEvidenceRef(), command.idempotencyKey(), command.correlationId(), command.decidedAt(),
      command.permissionGranted(), command.separationOfDutiesConfigured(), decisionPolicyCurrent
    );
  }
}
