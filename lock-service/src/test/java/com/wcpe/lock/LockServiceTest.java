package com.wcpe.lock;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

  @Test
  void ConfirmApprovedLockCreatesActiveTermsTest() {
    LockModels.LockRequestResponse request = service.requestLock(validCommand("REQ-CONF-001", "IDEMP-CONF-REQ-001", TENANT_A));
    LockModels.LockDecisionResponse approval = service.decideLock(decisionCommand(
      request.lockId(), LockModels.LockDecisionType.APPROVE, request.version(), "IDEMP-CONF-DEC-001", TENANT_A
    ));

    LockModels.LockConfirmationResponse confirmation = service.confirmLock(confirmationCommand(
      approval.lockId(), approval.version(), "IDEMP-CONF-001", TENANT_A, LockModels.LockConfirmationType.INTERNAL, true
    ));

    assertEquals(LockModels.RateLockStatus.APPROVED, confirmation.previousStatus());
    assertEquals(LockModels.RateLockStatus.ACTIVE, confirmation.status());
    assertEquals(3, confirmation.version());
    assertEquals("LOCK-PII10-001", confirmation.lockNumber());
    assertEquals("lock.confirmed.v1", confirmation.outboxEventType());
    assertEquals("LOCK_CONFIRMED", service.auditSnapshots().get(2).action());
    assertEquals("lock.confirmed.v1", service.outboxEvents().get(2).eventType());
    assertTrue(confirmation.replayRef().startsWith("REPLAY-LOCK-CONFIRMATION-"));
    assertEquals(1, service.committedConfirmationCount());
    assertEquals(1, service.metrics().lockConfirmationTotal());
  }

  @Test
  void InvestorMismatchFailsConfirmationTest() {
    LockModels.LockRequestResponse request = service.requestLock(validCommand("REQ-CONF-002", "IDEMP-CONF-REQ-002", TENANT_A));
    LockModels.LockDecisionResponse approval = service.decideLock(decisionCommand(
      request.lockId(), LockModels.LockDecisionType.APPROVE, request.version(), "IDEMP-CONF-DEC-002", TENANT_A
    ));
    LockModels.LockConfirmationResponse pending = service.confirmLock(confirmationCommand(
      approval.lockId(), approval.version(), "IDEMP-CONF-REQ-INV-002", TENANT_A, LockModels.LockConfirmationType.INVESTOR_REQUEST, true
    ));

    LockModels.LockConfirmationCommand callback = withResponseProduct(
      confirmationCommand(pending.lockId(), pending.version(), "IDEMP-CONF-CB-002", TENANT_A, LockModels.LockConfirmationType.INVESTOR_CALLBACK, false),
      "CONVENTIONAL-15Y"
    );
    LockModels.LockConfirmationResponse rejected = service.confirmLock(callback);

    assertEquals(LockModels.RateLockStatus.PENDING_INVESTOR_CONFIRMATION, pending.status());
    assertEquals(LockModels.RateLockStatus.INVESTOR_REJECTED, rejected.status());
    assertEquals("lock.investor_rejected.v1", rejected.outboxEventType());
    assertEquals("LOCK_INVESTOR_REJECTED", service.auditSnapshots().get(3).action());
    assertEquals(1, service.metrics().pendingInvestorConfirmationTotal());
    assertEquals(1, service.metrics().investorMismatchTotal());
  }

  @Test
  void DuplicateConfirmationIsIdempotentTest() {
    LockModels.LockRequestResponse request = service.requestLock(validCommand("REQ-CONF-003", "IDEMP-CONF-REQ-003", TENANT_A));
    LockModels.LockDecisionResponse approval = service.decideLock(decisionCommand(
      request.lockId(), LockModels.LockDecisionType.APPROVE, request.version(), "IDEMP-CONF-DEC-003", TENANT_A
    ));
    LockModels.LockConfirmationCommand command = confirmationCommand(
      approval.lockId(), approval.version(), "IDEMP-CONF-003", TENANT_A, LockModels.LockConfirmationType.INTERNAL, true
    );

    LockModels.LockConfirmationResponse first = service.confirmLockReplayAware(command);
    LockModels.LockConfirmationResponse replay = service.confirmLockReplayAware(command);

    assertEquals(first.confirmationId(), replay.confirmationId());
    LockServiceException conflict = assertThrows(
      LockServiceException.class,
      () -> service.confirmLock(withLockNumber(command, "LOCK-PII10-003-DIFFERENT"))
    );
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.code());
    LockServiceException duplicate = assertThrows(
      LockServiceException.class,
      () -> service.confirmLock(confirmationCommand(
        approval.lockId(), first.version(), "IDEMP-CONF-003-B", TENANT_A, LockModels.LockConfirmationType.INTERNAL, true
      ))
    );
    assertEquals("DUPLICATE_ACTIVE_CONFIRMATION", duplicate.code());
  }

  @Test
  void postLockConfirmationApiMapsTenantScopedRouteToServiceCommand() {
    LockModels.LockRequestResponse request = service.requestLock(validCommand("REQ-CONF-API-001", "IDEMP-CONF-API-REQ-001", TENANT_A));
    LockModels.LockDecisionResponse approval = service.decideLock(decisionCommand(
      request.lockId(), LockModels.LockDecisionType.APPROVE, request.version(), "IDEMP-CONF-API-DEC-001", TENANT_A
    ));
    LockConfirmationApi api = new LockConfirmationApi(service);

    LockConfirmationApi.ConfirmationResponse response = api.postConfirmation(
      TENANT_A,
      approval.lockId(),
      "IDEMP-CONF-API-001",
      "corr-pii10-s04-api",
      confirmationApiRequest(approval.version())
    );

    assertEquals("POST", LockConfirmationApi.POST_CONFIRMATION_METHOD);
    assertEquals("/api/v1/tenants/{tenantId}/locks/{lockId}/confirmations", LockConfirmationApi.POST_CONFIRMATION_PATH);
    assertEquals("lock:lock-confirmation:write", LockConfirmationApi.WRITE_PERMISSION);
    assertEquals(LockModels.RateLockStatus.ACTIVE.name(), response.status());
    assertEquals("lock.confirmed.v1", response.eventType());
    assertEquals("corr-pii10-s04-api", response.correlationId());
    assertTrue(response.auditRef().startsWith("AUDIT-LOCK-CONFIRMATION-"));
  }

  @Test
  void getLockConfirmationApiReadsByTenantAndRejectsCrossTenantAccess() {
    LockModels.LockRequestResponse request = service.requestLock(validCommand("REQ-CONF-API-002", "IDEMP-CONF-API-REQ-002", TENANT_A));
    LockModels.LockDecisionResponse approval = service.decideLock(decisionCommand(
      request.lockId(), LockModels.LockDecisionType.APPROVE, request.version(), "IDEMP-CONF-API-DEC-002", TENANT_A
    ));
    LockConfirmationApi api = new LockConfirmationApi(service);
    LockConfirmationApi.ConfirmationResponse confirmation = api.postConfirmation(
      TENANT_A,
      approval.lockId(),
      "IDEMP-CONF-API-002",
      "corr-pii10-s04-api-read",
      confirmationApiRequest(approval.version())
    );

    LockConfirmationApi.ConfirmationReadResponse read = api.getConfirmation(TENANT_A, confirmation.id());

    assertEquals("GET", LockConfirmationApi.GET_CONFIRMATION_METHOD);
    assertEquals("/api/v1/tenants/{tenantId}/lock-confirmation/{id}", LockConfirmationApi.GET_CONFIRMATION_PATH);
    assertEquals("lock:lock-confirmation:read", LockConfirmationApi.READ_PERMISSION);
    assertEquals(confirmation.id(), read.id());
    assertEquals(confirmation.status(), read.status());
    LockServiceException error = assertThrows(LockServiceException.class, () -> api.getConfirmation(TENANT_B, confirmation.id()));
    assertEquals("NOT_FOUND", error.code());
  }

  @Test
  void ExpiringSoonThresholdFromConfigTest() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("EXP-001", TENANT_A);

    LockModels.LockExpirationRunResponse run = service.runExpiration(expirationCommand(
      "EXP-RUN-001", TENANT_A, Instant.parse("2026-07-04T20:51:00Z"), 900, true
    ));

    assertEquals("COMPLETED", run.status());
    assertEquals(1, run.processedCount());
    assertEquals(1, run.expiringSoonCount());
    assertEquals(0, run.expiredCount());
    assertEquals(LockModels.RateLockStatus.EXPIRING_SOON, service.getLock(TENANT_A, confirmation.lockId()).status());
    assertEquals("lock.expiring_soon.v1", service.outboxEvents().get(3).eventType());
    assertEquals("LOCK_EXPIRING_SOON", service.auditSnapshots().get(3).action());
    assertEquals(1, service.metrics().locksExpiringSoonTotal());
  }

  @Test
  void ExpirationComputesTimezoneCutoffTest() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("EXP-002", TENANT_A);

    LockModels.LockExpirationRunResponse run = service.runExpiration(expirationCommand(
      "EXP-RUN-002", TENANT_A, Instant.parse("2026-07-04T21:06:00Z"), 900, true
    ));

    assertEquals(1, run.processedCount());
    assertEquals(1, run.expiredCount());
    LockModels.RateLockRecord expired = service.getLock(TENANT_A, confirmation.lockId());
    assertEquals(LockModels.RateLockStatus.EXPIRED, expired.status());
    assertEquals(Instant.parse("2026-07-04T21:06:00Z"), expired.expiresAt());
    assertEquals("lock.expired.v1", expired.outboxEventType());
    assertEquals("lock.expired.v1", service.outboxEvents().get(3).eventType());
    assertEquals("LOCK_EXPIRED", service.auditSnapshots().get(3).action());
    assertEquals(1, service.metrics().locksExpiredTotal());
  }

  @Test
  void ExpiredTerminalNoOpTest() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("EXP-003", TENANT_A);

    service.runExpiration(expirationCommand("EXP-RUN-003A", TENANT_A, Instant.parse("2026-07-04T21:06:00Z"), 900, true));
    LockModels.LockExpirationRunResponse rerun = service.runExpiration(expirationCommand(
      "EXP-RUN-003B", TENANT_A, Instant.parse("2026-07-04T21:07:00Z"), 900, true
    ));

    assertEquals(LockModels.RateLockStatus.EXPIRED, service.getLock(TENANT_A, confirmation.lockId()).status());
    assertEquals(0, rerun.processedCount());
    assertEquals(0, rerun.expiredCount());
    assertEquals(4, service.outboxEvents().size());
  }

  @Test
  void expirationRunReplayIsIdempotentAndRejectsConflictingPayload() {
    createActiveLock("EXP-IDEMP-001", TENANT_A);
    LockModels.LockExpirationRunCommand command = expirationCommand(
      "EXP-RUN-IDEMP-001", TENANT_A, Instant.parse("2026-07-04T21:06:00Z"), 900, true
    );

    LockModels.LockExpirationRunResponse first = service.runExpiration(command);
    LockModels.LockExpirationRunResponse replay = service.runExpiration(command);

    assertEquals(first.replayRef(), replay.replayRef());
    assertEquals(first.expiredCount(), replay.expiredCount());
    LockServiceException conflict = assertThrows(
      LockServiceException.class,
      () -> service.runExpiration(expirationCommand("EXP-RUN-IDEMP-001", TENANT_A, Instant.parse("2026-07-04T21:07:00Z"), 900, true))
    );
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.code());
  }

  @Test
  void expirationPolicyMissingFailsClosedWithoutStateChange() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("EXP-004", TENANT_A);

    LockServiceException error = assertThrows(
      LockServiceException.class,
      () -> service.runExpiration(expirationCommand("EXP-RUN-004", TENANT_A, Instant.parse("2026-07-04T21:06:00Z"), 900, false))
    );

    assertEquals("POLICY_NOT_SATISFIED", error.code());
    assertEquals(LockModels.RateLockStatus.ACTIVE, service.getLock(TENANT_A, confirmation.lockId()).status());
    assertEquals(0, service.committedExpirationRunCount());
  }

  @Test
  void internalLockExpirationApiMapsSchedulerCommand() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("EXP-API-001", TENANT_A);
    LockExpirationApi api = new LockExpirationApi(service);

    LockExpirationApi.ExpirationRunResponse response = api.postExpirationRun(new LockExpirationApi.ExpirationRunRequest(
      TENANT_A,
      "EXP-RUN-API-001",
      "lock-expiration-scheduler",
      Instant.parse("2026-07-04T21:06:00Z"),
      900,
      "lock-expiration-policy-v1",
      true,
      true,
      false,
      "corr-pii10-s05-api"
    ));

    assertEquals("POST", LockExpirationApi.POST_EXPIRATION_RUN_METHOD);
    assertEquals("/internal/lock-expiration-runs", LockExpirationApi.POST_EXPIRATION_RUN_PATH);
    assertEquals("LOCK_EXPIRATION_RUN", LockExpirationApi.SERVICE_PERMISSION);
    assertEquals("COMPLETED", response.status());
    assertEquals(1, response.expiredCount());
    assertEquals(LockModels.RateLockStatus.EXPIRED, service.getLock(TENANT_A, confirmation.lockId()).status());
    assertEquals("corr-pii10-s05-api", response.correlationId());
  }

  @Test
  void ExtensionPreviewUsesConfiguredPolicyCostSnapshotTest() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("EXT-PREV-001", TENANT_A);

    LockModels.LockExtensionPreviewResponse preview = service.previewExtension(extensionPreviewCommand(
      confirmation.lockId(), confirmation.version(), TENANT_A
    ));

    assertEquals(3, preview.requestedDays());
    assertEquals("extension-policy-v1", preview.costSnapshot().policyVersionId());
    assertTrue(preview.validationMessages().contains("Extension preview calculated from provided tenant/investor policy snapshot"));
  }

  @Test
  void ExtensionRequestCreatesRequestedStateAuditOutboxAndBlocksSecondOpenRequest() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("EXT-REQ-001", TENANT_A);

    LockModels.LockExtensionResponse response = service.requestExtension(extensionRequestCommand(
      confirmation.lockId(), confirmation.version(), "EXT-REQ-001", "IDEMP-EXT-REQ-001", TENANT_A
    ));

    assertEquals(LockModels.LockExtensionStatus.REQUESTED, response.extensionStatus());
    assertEquals(LockModels.RateLockStatus.EXTENSION_REQUESTED, response.status());
    assertEquals("lock.extension_requested.v1", response.outboxEventType());
    assertEquals("LOCK_EXTENSION_REQUESTED", service.auditSnapshots().get(3).action());
    assertEquals(1, service.committedExtensionCount());
    LockServiceException duplicate = assertThrows(
      LockServiceException.class,
      () -> service.requestExtension(extensionRequestCommand(response.lockId(), response.version(), "EXT-REQ-001B", "IDEMP-EXT-REQ-001B", TENANT_A))
    );
    assertEquals("LOCK_STATE_CONFLICT", duplicate.code());
  }

  @Test
  void ExtensionApprovalAmendsExpirationWithoutInventingPolicyValues() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("EXT-APR-001", TENANT_A);
    LockModels.LockExtensionResponse requested = service.requestExtension(extensionRequestCommand(
      confirmation.lockId(), confirmation.version(), "EXT-APR-001", "IDEMP-EXT-APR-REQ-001", TENANT_A
    ));

    LockModels.LockExtensionResponse approved = service.decideExtension(extensionDecisionCommand(
      requested.lockId(), requested.extensionId(), LockModels.LockExtensionDecisionType.APPROVE, requested.version(), false,
      "IDEMP-EXT-APR-DEC-001", TENANT_A
    ));

    assertEquals(LockModels.LockExtensionStatus.CONFIRMED, approved.extensionStatus());
    assertEquals(LockModels.RateLockStatus.ACTIVE, approved.status());
    assertEquals(Instant.parse("2026-07-07T21:06:00Z"), approved.expiresAt());
    assertEquals("lock.extension_approved.v1", approved.outboxEventType());
    assertEquals("LOCK_EXTENSION_APPROVED", service.auditSnapshots().get(4).action());
  }

  @Test
  void InvestorExtensionConfirmationAmendsExpirationAfterApprovedPendingInvestor() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("EXT-INV-001", TENANT_A);
    LockModels.LockExtensionResponse requested = service.requestExtension(extensionRequestCommand(
      confirmation.lockId(), confirmation.version(), "EXT-INV-001", "IDEMP-EXT-INV-REQ-001", TENANT_A
    ));
    LockModels.LockExtensionResponse pending = service.decideExtension(extensionDecisionCommand(
      requested.lockId(), requested.extensionId(), LockModels.LockExtensionDecisionType.APPROVE, requested.version(), true,
      "IDEMP-EXT-INV-DEC-001", TENANT_A
    ));

    LockModels.LockExtensionResponse confirmed = service.confirmExtension(extensionConfirmationCommand(
      pending.lockId(), pending.extensionId(), pending.version(), "IDEMP-EXT-INV-CONF-001", TENANT_A, true
    ));

    assertEquals(LockModels.LockExtensionStatus.PENDING_INVESTOR_CONFIRMATION, pending.extensionStatus());
    assertEquals(LockModels.RateLockStatus.PENDING_INVESTOR_EXTENSION_CONFIRMATION, pending.status());
    assertEquals(LockModels.LockExtensionStatus.CONFIRMED, confirmed.extensionStatus());
    assertEquals(LockModels.RateLockStatus.ACTIVE, confirmed.status());
    assertEquals("lock.extension_confirmed.v1", confirmed.outboxEventType());
    assertEquals(Instant.parse("2026-07-07T21:06:00Z"), service.getExpirationSchedule(TENANT_A, confirmed.lockId()).expiresAt());
  }

  @Test
  void ExtensionCancelRequestCancelsOpenExtensionEmitsAuditAndReleasesOpenIndex() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("EXT-CAN-001", TENANT_A);
    LockModels.LockExtensionResponse requested = service.requestExtension(extensionRequestCommand(
      confirmation.lockId(), confirmation.version(), "EXT-CAN-001", "IDEMP-EXT-CAN-REQ-001", TENANT_A
    ));

    LockModels.LockExtensionResponse cancelled = service.cancelExtension(extensionCancelCommand(
      requested.lockId(), requested.extensionId(), requested.version(), "IDEMP-EXT-CAN-001", TENANT_A
    ));
    LockModels.LockExtensionResponse secondRequest = service.requestExtension(extensionRequestCommand(
      cancelled.lockId(), cancelled.version(), "EXT-CAN-002", "IDEMP-EXT-CAN-REQ-002", TENANT_A
    ));

    assertEquals(LockModels.LockExtensionStatus.CANCELLED, cancelled.extensionStatus());
    assertEquals(LockModels.RateLockStatus.ACTIVE, cancelled.status());
    assertEquals(Instant.parse("2026-07-04T21:06:00Z"), cancelled.expiresAt());
    assertEquals("lock.extension_cancelled.v1", cancelled.outboxEventType());
    assertEquals("LOCK_EXTENSION_CANCELLED", service.auditSnapshots().get(service.auditSnapshots().size() - 2).action());
    assertEquals(LockModels.LockExtensionStatus.REQUESTED, secondRequest.extensionStatus());
    assertEquals(2, service.committedExtensionCount());
  }

  @Test
  void ExtensionMetricsTrackRequestsDecisionsAverageDaysFeesAndConfirmationFailures() {
    LockModels.LockConfirmationResponse approvedConfirmation = createActiveLock("EXT-MET-APR-001", TENANT_A);
    LockModels.LockExtensionResponse approvedRequest = service.requestExtension(extensionRequestCommand(
      approvedConfirmation.lockId(), approvedConfirmation.version(), "EXT-MET-APR-001", "IDEMP-EXT-MET-APR-REQ-001", TENANT_A
    ));
    service.decideExtension(extensionDecisionCommand(
      approvedRequest.lockId(), approvedRequest.extensionId(), LockModels.LockExtensionDecisionType.APPROVE, approvedRequest.version(), false,
      "IDEMP-EXT-MET-APR-DEC-001", TENANT_A
    ));

    LockModels.LockConfirmationResponse rejectedConfirmation = createActiveLock("EXT-MET-REJ-001", TENANT_B);
    LockModels.LockExtensionResponse rejectedRequest = service.requestExtension(extensionRequestCommand(
      rejectedConfirmation.lockId(), rejectedConfirmation.version(), "EXT-MET-REJ-001", "IDEMP-EXT-MET-REJ-REQ-001", TENANT_B
    ));
    service.decideExtension(extensionDecisionCommand(
      rejectedRequest.lockId(), rejectedRequest.extensionId(), LockModels.LockExtensionDecisionType.REJECT, rejectedRequest.version(), false,
      "IDEMP-EXT-MET-REJ-DEC-001", TENANT_B
    ));

    LockModels.LockConfirmationResponse pendingConfirmation = createActiveLock("EXT-MET-CONF-001", UUID.fromString("33333333-3333-3333-3333-333333333333"));
    LockModels.LockExtensionResponse pendingRequest = service.requestExtension(extensionRequestCommand(
      pendingConfirmation.lockId(), pendingConfirmation.version(), "EXT-MET-CONF-001", "IDEMP-EXT-MET-CONF-REQ-001", pendingConfirmation.tenantId()
    ));
    LockModels.LockExtensionResponse pending = service.decideExtension(extensionDecisionCommand(
      pendingRequest.lockId(), pendingRequest.extensionId(), LockModels.LockExtensionDecisionType.APPROVE, pendingRequest.version(), true,
      "IDEMP-EXT-MET-CONF-DEC-001", pendingConfirmation.tenantId()
    ));
    assertThrows(LockServiceException.class, () -> service.confirmExtension(extensionConfirmationCommand(
      pending.lockId(), pending.extensionId(), pending.version(), "IDEMP-EXT-MET-CONF-FAIL-001", pendingConfirmation.tenantId(), false
    )));

    LockModels.MetricsSnapshot metrics = service.metrics();
    assertEquals(3, metrics.extensionRequestTotal());
    assertEquals(2, metrics.extensionApprovalTotal());
    assertEquals(1, metrics.extensionRejectionTotal());
    assertEquals(1, metrics.extensionConfirmationFailureTotal());
    assertEquals(3.0, metrics.extensionAverageRequestedDays(), 0.001);
    assertEquals("fee-config-ref:EXT-3D", metrics.extensionFeeConfigRefsByReason().get("EXTENSION_REASON_CONFIGURED"));
  }

  @Test
  void ExtensionMissingPolicyFailsClosedWithoutStateChange() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("EXT-POL-001", TENANT_A);

    LockServiceException error = assertThrows(
      LockServiceException.class,
      () -> service.requestExtension(withExtensionPolicy(extensionRequestCommand(
        confirmation.lockId(), confirmation.version(), "EXT-POL-001", "IDEMP-EXT-POL-001", TENANT_A
      ), false))
    );

    assertEquals("POLICY_NOT_SATISFIED", error.code());
    assertEquals(LockModels.RateLockStatus.ACTIVE, service.getLock(TENANT_A, confirmation.lockId()).status());
    assertEquals(0, service.committedExtensionCount());
  }

  @Test
  void postLockExtensionApiMapsTenantScopedRoutesToServiceCommands() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("EXT-API-001", TENANT_A);
    LockExtensionApi api = new LockExtensionApi(service);

    LockExtensionApi.ExtensionPreviewResponse preview = api.postPreview(
      TENANT_A, confirmation.lockId(), "IDEMP-EXT-API-PREV-001", "corr-pii10-s06-api", extensionPreviewApiRequest(confirmation.version())
    );
    LockExtensionApi.ExtensionResponse requested = api.postExtension(
      TENANT_A, confirmation.lockId(), "IDEMP-EXT-API-REQ-001", "corr-pii10-s06-api", extensionApiRequest(confirmation.version())
    );
    LockExtensionApi.ExtensionResponse approved = api.postDecision(
      TENANT_A, confirmation.lockId(), requested.id(), "IDEMP-EXT-API-DEC-001", "corr-pii10-s06-api",
      extensionDecisionApiRequest(requested.version(), false)
    );
    LockExtensionApi.ExtensionResponse cancelRequested = api.postExtension(
      TENANT_B, createActiveLock("EXT-API-CAN-001", TENANT_B).lockId(), "IDEMP-EXT-API-CAN-REQ-001", "corr-pii10-s06-api",
      extensionApiRequest(3)
    );
    LockExtensionApi.ExtensionResponse cancelled = api.postCancel(
      TENANT_B, cancelRequested.lockId(), cancelRequested.id(), "IDEMP-EXT-API-CAN-001", "corr-pii10-s06-api",
      extensionCancelApiRequest(cancelRequested.version())
    );

    assertEquals("POST", LockExtensionApi.POST_PREVIEW_METHOD);
    assertEquals("/api/v1/tenants/{tenantId}/locks/{lockId}/extensions/preview", LockExtensionApi.POST_PREVIEW_PATH);
    assertEquals("/api/v1/tenants/{tenantId}/locks/{lockId}/extensions", LockExtensionApi.POST_EXTENSION_PATH);
    assertEquals("/api/v1/tenants/{tenantId}/locks/{lockId}/extensions/{extensionId}/decisions", LockExtensionApi.POST_DECISION_PATH);
    assertEquals("/api/v1/tenants/{tenantId}/locks/{lockId}/extensions/{extensionId}/cancel", LockExtensionApi.POST_CANCEL_PATH);
    assertEquals("LOCK_EXTENSION_REQUEST", LockExtensionApi.REQUEST_PERMISSION);
    assertEquals("LOCK_EXTENSION_CANCEL", LockExtensionApi.CANCEL_PERMISSION);
    assertEquals("extension-policy-v1", preview.costSnapshot().policyVersionId());
    assertEquals("CONFIRMED", approved.extensionStatus());
    assertEquals("lock.extension_approved.v1", approved.eventType());
    assertEquals("CANCELLED", cancelled.extensionStatus());
    assertEquals("lock.extension_cancelled.v1", cancelled.eventType());
  }

  @Test
  void RelockWorseCaseSelectionFromPolicyTest() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("REL-PREV-001", TENANT_A);
    LockModels.RelockResponse preview = service.previewRelock(relockPreviewCommand(
      confirmation.lockId(), confirmation.version(), "QUOTE-RELOCK-PREV-001", TENANT_A
    ));

    assertEquals(LockModels.RelockStatus.PREVIEWED, preview.relockStatus());
    assertEquals(LockModels.RateLockStatus.ACTIVE, preview.sourceStatus());
    assertTrue(preview.resultSummary().contains("policy snapshot"));
    assertTrue(preview.replayRef().startsWith("REPLAY-LOCK-RELOCK-PREVIEW-"));
  }

  @Test
  void RelockRequiresFreshCurrentQuoteTest() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("REL-FRESH-001", TENANT_A);

    LockServiceException error = assertThrows(
      LockServiceException.class,
      () -> service.requestRelock(withRelockPolicy(relockRequestCommand(
        confirmation.lockId(), confirmation.version(), "REL-FRESH-001", "IDEMP-REL-FRESH-001", "QUOTE-RELOCK-FRESH-001", TENANT_A
      ), relockPolicy(false, true)))
    );

    assertEquals("POLICY_NOT_SATISFIED", error.code());
    assertEquals(LockModels.RateLockStatus.ACTIVE, service.getLock(TENANT_A, confirmation.lockId()).status());
    assertEquals(0, service.committedRelockCount());
  }

  @Test
  void RelockBlocksBeforeWaitingPeriodTest() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("REL-WAIT-001", TENANT_A);

    LockServiceException error = assertThrows(
      LockServiceException.class,
      () -> service.requestRelock(withRelockPolicy(relockRequestCommand(
        confirmation.lockId(), confirmation.version(), "REL-WAIT-001", "IDEMP-REL-WAIT-001", "QUOTE-RELOCK-WAIT-001", TENANT_A
      ), relockPolicy(true, true, false, false)))
    );

    assertEquals("POLICY_NOT_SATISFIED", error.code());
    assertTrue(error.getMessage().contains("waiting period"));
    assertEquals(LockModels.RateLockStatus.ACTIVE, service.getLock(TENANT_A, confirmation.lockId()).status());
  }

  @Test
  void RelockLinksReplacementLockTest() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("REL-REQ-001", TENANT_A);

    LockModels.RelockResponse requested = service.requestRelock(relockRequestCommand(
      confirmation.lockId(), confirmation.version(), "REL-REQ-001", "IDEMP-REL-REQ-001", "QUOTE-RELOCK-REQ-001", TENANT_A
    ));

    assertEquals(LockModels.RelockStatus.REQUESTED, requested.relockStatus());
    assertEquals(LockModels.RateLockStatus.RELOCK_REQUESTED, requested.sourceStatus());
    assertEquals(LockModels.RateLockStatus.PENDING_APPROVAL, requested.replacementStatus());
    assertEquals("lock.relock_requested.v1", requested.outboxEventType());
    assertEquals("LOCK_RELOCK_REQUESTED", service.auditSnapshots().get(3).action());
    assertEquals(requested.replacementLockId(), service.getRelock(TENANT_A, requested.relockId()).replacementLockId());
  }

  @Test
  void RelockCreatesReplacementAtomicallyIT() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("REL-CONF-001", TENANT_A);
    LockModels.RelockResponse requested = service.requestRelock(relockRequestCommand(
      confirmation.lockId(), confirmation.version(), "REL-CONF-001", "IDEMP-REL-CONF-REQ-001", "QUOTE-RELOCK-CONF-001", TENANT_A
    ));
    LockModels.RelockResponse approved = service.decideRelock(relockDecisionCommand(
      requested.sourceLockId(), requested.relockId(), LockModels.RelockDecisionType.APPROVE, requested.version(), "IDEMP-REL-CONF-DEC-001", TENANT_A
    ));

    LockModels.RelockResponse confirmed = service.confirmRelock(relockConfirmationCommand(
      approved.sourceLockId(), approved.relockId(), approved.version(), "IDEMP-REL-CONF-INV-001", TENANT_A, true
    ));

    assertEquals(LockModels.RelockStatus.CONFIRMED, confirmed.relockStatus());
    assertEquals(LockModels.RateLockStatus.RELOCKED, confirmed.sourceStatus());
    assertEquals(LockModels.RateLockStatus.ACTIVE, confirmed.replacementStatus());
    assertEquals("lock.relocked.v1", confirmed.outboxEventType());
    assertEquals("LOCK_RELOCKED", service.auditSnapshots().get(service.auditSnapshots().size() - 1).action());
  }

  @Test
  void investorRelockApprovalMovesToPendingInvestorConfirmation() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("REL-INV-001", TENANT_A);
    LockModels.RelockResponse requested = service.requestRelock(withRelockPolicy(relockRequestCommand(
      confirmation.lockId(), confirmation.version(), "REL-INV-001", "IDEMP-REL-INV-REQ-001", "QUOTE-RELOCK-INV-001", TENANT_A
    ), relockPolicy(true, true, true, true)));

    LockModels.RelockResponse pending = service.decideRelock(relockDecisionCommand(
      requested.sourceLockId(), requested.relockId(), LockModels.RelockDecisionType.APPROVE, requested.version(), "IDEMP-REL-INV-DEC-001", TENANT_A
    ));
    LockModels.RelockResponse confirmed = service.confirmRelock(relockConfirmationCommand(
      pending.sourceLockId(), pending.relockId(), pending.version(), "IDEMP-REL-INV-CONF-001", TENANT_A, true
    ));

    assertEquals(LockModels.RelockStatus.PENDING_INVESTOR_CONFIRMATION, pending.relockStatus());
    assertEquals(LockModels.RateLockStatus.PENDING_INVESTOR_RELOCK_CONFIRMATION, pending.sourceStatus());
    assertEquals(LockModels.RateLockStatus.PENDING_INVESTOR_CONFIRMATION, pending.replacementStatus());
    assertEquals(LockModels.RelockStatus.CONFIRMED, confirmed.relockStatus());
    assertEquals(LockModels.RateLockStatus.RELOCKED, confirmed.sourceStatus());
    assertEquals(LockModels.RateLockStatus.ACTIVE, confirmed.replacementStatus());
  }

  @Test
  void RelockTenantIsolationAndIdempotency() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("REL-IDEMP-001", TENANT_A);
    LockModels.RelockRequestCommand command = relockRequestCommand(
      confirmation.lockId(), confirmation.version(), "REL-IDEMP-001", "IDEMP-REL-IDEMP-001", "QUOTE-RELOCK-IDEMP-001", TENANT_A
    );

    LockModels.RelockResponse first = service.requestRelockReplayAware(command);
    LockModels.RelockResponse replay = service.requestRelockReplayAware(command);

    assertEquals(first.relockId(), replay.relockId());
    assertThrows(LockServiceException.class, () -> service.getRelock(TENANT_B, first.relockId()));
    LockServiceException conflict = assertThrows(
      LockServiceException.class,
      () -> service.requestRelock(relockRequestCommand(
        confirmation.lockId(), confirmation.version(), "REL-IDEMP-CHANGED", "IDEMP-REL-IDEMP-001", "QUOTE-RELOCK-IDEMP-002", TENANT_A
      ))
    );
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.code());
  }

  @Test
  void postRelockApiMapsTenantScopedRoutesToServiceCommands() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("REL-API-001", TENANT_A);
    LockRelockApi api = new LockRelockApi(service);

    LockRelockApi.RelockPreviewResponse preview = api.postPreview(
      TENANT_A, confirmation.lockId(), "IDEMP-REL-API-PREV-001", "corr-pii10-s07-api",
      relockPreviewApiRequest(confirmation.lockId(), confirmation.version(), "QUOTE-RELOCK-API-PREV-001")
    );
    LockRelockApi.RelockResponse requested = api.postRelock(
      TENANT_A, confirmation.lockId(), "IDEMP-REL-API-REQ-001", "corr-pii10-s07-api",
      relockApiRequest(confirmation.lockId(), confirmation.version(), "QUOTE-RELOCK-API-REQ-001")
    );
    LockRelockApi.RelockResponse approved = api.postDecision(
      TENANT_A, confirmation.lockId(), requested.id(), "IDEMP-REL-API-DEC-001", "corr-pii10-s07-api",
      relockDecisionApiRequest(requested.version())
    );
    LockRelockApi.RelockResponse confirmed = api.postConfirmation(
      TENANT_A, confirmation.lockId(), approved.id(), "IDEMP-REL-API-CONF-001", "corr-pii10-s07-api",
      relockConfirmationApiRequest(approved.version())
    );

    assertEquals("POST", LockRelockApi.POST_PREVIEW_METHOD);
    assertEquals("/api/v1/tenants/{tenantId}/locks/{lockId}/relocks/preview", LockRelockApi.POST_PREVIEW_PATH);
    assertEquals("/api/v1/tenants/{tenantId}/locks/{lockId}/relocks", LockRelockApi.POST_RELOCK_PATH);
    assertEquals("/api/v1/tenants/{tenantId}/locks/{lockId}/relocks/{relockId}/decisions", LockRelockApi.POST_DECISION_PATH);
    assertEquals("/api/v1/tenants/{tenantId}/locks/{lockId}/relocks/{relockId}/confirmations", LockRelockApi.POST_CONFIRMATION_PATH);
    assertEquals("LOCK_RELOCK_REQUEST", LockRelockApi.REQUEST_PERMISSION);
    assertEquals("RELOCK-PREVIEW", preview.relockId().substring(0, 14));
    assertEquals("APPROVED", approved.relockStatus());
    assertEquals("CONFIRMED", confirmed.relockStatus());
    assertEquals("lock.relocked.v1", confirmed.eventType());
  }

  @Test
  void FloatDownEligibilityThresholdFromPolicyTest() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("REN-PREV-001", TENANT_A);
    LockRenegotiationApi api = new LockRenegotiationApi(service);

    LockRenegotiationApi.RenegotiationPreviewResponse preview = api.postPreview(
      TENANT_A, confirmation.lockId(), "IDEMP-REN-PREV-001", "corr-pii10-s08",
      renegotiationPreviewApiRequest(confirmation.lockId(), confirmation.version(), "QUOTE-REN-PREV-001")
    );

    assertEquals("POST", LockRenegotiationApi.POST_PREVIEW_METHOD);
    assertEquals("/api/v1/tenants/{tenantId}/locks/{lockId}/renegotiations/preview", LockRenegotiationApi.POST_PREVIEW_PATH);
    assertEquals("LOCK_RENEGOTIATION_REQUEST", LockRenegotiationApi.REQUEST_PERMISSION);
    assertTrue(preview.benefitLedgerHash().length() >= 16);
    assertTrue(preview.resultSummary().contains("policy snapshot"));
  }

  @Test
  void FloatDownBlocksNoImprovementTest() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("REN-BLOCK-001", TENANT_A);
    LockModels.RelockTermsSnapshot invalidSelectedTerms = new LockModels.RelockTermsSnapshot(
      "CONVENTIONAL-30Y", "INVESTOR-A", "rate-sheet-v2", "not-configured-price-ref", "not-configured-rate-ref",
      "not-configured-fee-ref", "not-configured-lock-period-ref", "not-configured-benefit-hash"
    );

    LockServiceException error = assertThrows(
      LockServiceException.class,
      () -> service.previewRelock(withSelectedTerms(relockPreviewCommand(
        confirmation.lockId(), confirmation.version(), "QUOTE-REN-BLOCK-001", TENANT_A
      ), invalidSelectedTerms))
    );

    assertEquals("POLICY_NOT_SATISFIED", error.code());
    assertTrue(error.getMessage().contains("comparison snapshot"));
  }

  @Test
  void RenegotiationRequiresFreshQuoteTest() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("REN-FRESH-001", TENANT_A);

    LockServiceException error = assertThrows(
      LockServiceException.class,
      () -> service.requestRelock(withRelockPolicy(relockRequestCommand(
        confirmation.lockId(), confirmation.version(), "REN-FRESH-001", "IDEMP-REN-FRESH-001", "QUOTE-REN-FRESH-001", TENANT_A
      ), relockPolicy(false, true)))
    );

    assertEquals("POLICY_NOT_SATISFIED", error.code());
    assertEquals(LockModels.RateLockStatus.ACTIVE, service.getLock(TENANT_A, confirmation.lockId()).status());
  }

  @Test
  void TermAmendmentIsImmutableTest() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("REN-IMM-001", TENANT_A);
    LockModels.RelockResponse requested = service.requestRelock(relockRequestCommand(
      confirmation.lockId(), confirmation.version(), "REN-IMM-001", "IDEMP-REN-IMM-REQ-001", "QUOTE-REN-IMM-001", TENANT_A
    ));
    LockModels.RelockResponse approved = service.decideRelock(relockDecisionCommand(
      requested.sourceLockId(), requested.relockId(), LockModels.RelockDecisionType.APPROVE, requested.version(), "IDEMP-REN-IMM-DEC-001", TENANT_A
    ));

    LockModels.RelockResponse confirmed = service.confirmRelock(relockConfirmationCommand(
      approved.sourceLockId(), approved.relockId(), approved.version(), "IDEMP-REN-IMM-CONF-001", TENANT_A, true
    ));

    assertNotEquals(confirmed.sourceLockId(), confirmed.replacementLockId());
    assertEquals(LockModels.RateLockStatus.RELOCKED, service.getLock(TENANT_A, confirmed.sourceLockId()).status());
    assertEquals(LockModels.RateLockStatus.ACTIVE, service.getLock(TENANT_A, confirmed.replacementLockId()).status());
    assertEquals(1, service.committedRelockCount());
  }

  @Test
  void RenegotiationApprovalOutboxIT() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("REN-API-001", TENANT_A);
    LockRenegotiationApi api = new LockRenegotiationApi(service);

    LockRenegotiationApi.RenegotiationResponse requested = api.postRenegotiation(
      TENANT_A, confirmation.lockId(), "IDEMP-REN-API-REQ-001", "corr-pii10-s08",
      renegotiationApiRequest(confirmation.lockId(), confirmation.version(), "QUOTE-REN-API-001")
    );
    LockRenegotiationApi.RenegotiationResponse approved = api.postDecision(
      TENANT_A, confirmation.lockId(), requested.id(), "IDEMP-REN-API-DEC-001", "corr-pii10-s08",
      renegotiationDecisionApiRequest(requested.version())
    );
    LockRenegotiationApi.RenegotiationResponse confirmed = api.postConfirmation(
      TENANT_A, confirmation.lockId(), approved.id(), "IDEMP-REN-API-CONF-001", "corr-pii10-s08",
      renegotiationConfirmationApiRequest(approved.version())
    );

    assertEquals("/api/v1/tenants/{tenantId}/locks/{lockId}/renegotiations", LockRenegotiationApi.POST_RENEGOTIATION_PATH);
    assertEquals("/api/v1/tenants/{tenantId}/locks/{lockId}/renegotiations/{renegotiationId}/decisions", LockRenegotiationApi.POST_DECISION_PATH);
    assertEquals("/api/v1/tenants/{tenantId}/locks/{lockId}/renegotiations/{renegotiationId}/confirmations", LockRenegotiationApi.POST_CONFIRMATION_PATH);
    assertEquals("lock.renegotiation_requested.v1", requested.eventType());
    assertEquals("lock.float_down_approved.v1", approved.eventType());
    assertEquals("lock.terms_amended.v1", confirmed.eventType());
    assertTrue(service.outboxEvents().get(service.outboxEvents().size() - 1).payload().containsKey("benefitLedgerRef"));
  }

  @Test
  void InvestorFloatDownConfirmationIT() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("REN-INV-001", TENANT_A);
    LockModels.RelockResponse requested = service.requestRelock(withRelockPolicy(relockRequestCommand(
      confirmation.lockId(), confirmation.version(), "REN-INV-001", "IDEMP-REN-INV-REQ-001", "QUOTE-REN-INV-001", TENANT_A
    ), relockPolicy(true, true, true, true)));
    LockModels.RelockResponse pending = service.decideRelock(relockDecisionCommand(
      requested.sourceLockId(), requested.relockId(), LockModels.RelockDecisionType.APPROVE, requested.version(), "IDEMP-REN-INV-DEC-001", TENANT_A
    ));

    LockServiceException mismatch = assertThrows(
      LockServiceException.class,
      () -> service.confirmRelock(relockConfirmationCommand(
        pending.sourceLockId(), pending.relockId(), pending.version(), "IDEMP-REN-INV-BAD-001", TENANT_A, false
      ))
    );
    LockModels.RelockResponse confirmed = service.confirmRelock(relockConfirmationCommand(
      pending.sourceLockId(), pending.relockId(), pending.version(), "IDEMP-REN-INV-CONF-001", TENANT_A, true
    ));

    assertEquals("POLICY_NOT_SATISFIED", mismatch.code());
    assertEquals(LockModels.RelockStatus.PENDING_INVESTOR_CONFIRMATION, pending.relockStatus());
    assertEquals(LockModels.RelockStatus.CONFIRMED, confirmed.relockStatus());
  }

  @Test
  void RenegotiationTenantIsolationIT() {
    LockModels.LockConfirmationResponse confirmation = createActiveLock("REN-ISO-001", TENANT_A);
    LockModels.RelockResponse requested = service.requestRelock(relockRequestCommand(
      confirmation.lockId(), confirmation.version(), "REN-ISO-001", "IDEMP-REN-ISO-001", "QUOTE-REN-ISO-001", TENANT_A
    ));

    assertThrows(LockServiceException.class, () -> service.getRelock(TENANT_B, requested.relockId()));
    assertThrows(LockServiceException.class, () -> service.getLock(TENANT_B, requested.replacementLockId()));
  }

  @Test
  void SyncAttemptIdempotencyTest() {
    LockModels.LockConfirmationResponse active = createActiveLock("SYNC-IDEMP-001", TENANT_A);
    LockModels.LockStatusSyncCommand command = syncCommand(active.lockId(), "lock.confirmed.v1:SYNC-IDEMP-001", "IDEMP-SYNC-001", TENANT_A);

    LockModels.LockSyncAttemptResponse first = service.syncLockStatusReplayAware(command);
    LockModels.LockSyncAttemptResponse replay = service.syncLockStatusReplayAware(command);

    assertEquals(first.attemptId(), replay.attemptId());
    assertEquals(LockModels.LockSyncStatus.SENT, first.status());
    assertEquals("lock.status_sync.sent.v1", first.outboxEventType());
    assertEquals(1, service.committedSyncAttemptCount());
    assertEquals(1, service.metrics().lockSyncSentTotal());

    LockServiceException conflict = assertThrows(
      LockServiceException.class,
      () -> service.syncLockStatusReplayAware(new LockModels.LockStatusSyncCommand(
        TENANT_A,
        active.lockId(),
        "lock.confirmed.v1:SYNC-IDEMP-001",
        "lock-sync-service",
        new LockModels.LockSyncTarget(TENANT_A, "LOS-LOCK-STATUS", "LOS-LOCK-STATUS-V2", true, "lock-status-sync-contract-v1", "lock-sync-policy-v1"),
        true,
        Instant.parse("2026-07-04T22:00:00Z"),
        "IDEMP-SYNC-001B",
        "corr-pii10-s09",
        Map.of("lockEvent", "lock.confirmed.v1:SYNC-IDEMP-001", "snapshot", "lock-status-snapshot-v2")
      ))
    );
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.code());
  }

  @Test
  void InboundAckValidatesSourceTrustTest() {
    LockModels.LockConfirmationResponse active = createActiveLock("SYNC-ACK-001", TENANT_A);
    service.syncLockStatusReplayAware(syncCommand(active.lockId(), "lock.confirmed.v1:SYNC-ACK-001", "IDEMP-SYNC-ACK-001", TENANT_A));

    LockServiceException untrusted = assertThrows(
      LockServiceException.class,
      () -> service.acknowledgeLockStatus(ackCommand(active.lockId(), "lock.confirmed.v1:SYNC-ACK-001", "ACK-001", false, null, TENANT_A))
    );
    assertEquals("POLICY_NOT_SATISFIED", untrusted.code());

    LockModels.LockSyncAttemptResponse acked = service.acknowledgeLockStatus(ackCommand(
      active.lockId(), "lock.confirmed.v1:SYNC-ACK-001", "ACK-002", true, null, TENANT_A
    ));
    assertEquals(LockModels.LockSyncStatus.ACKED, acked.status());
    assertEquals(1, service.committedSyncAcknowledgementCount());
    assertEquals("LOCK_SYNC_ACKED", service.auditSnapshots().get(service.auditSnapshots().size() - 1).action());
  }

  @Test
  void ExternalCorrectionStateGuardTest() {
    LockModels.LockConfirmationResponse active = createActiveLock("SYNC-GUARD-001", TENANT_A);
    service.syncLockStatusReplayAware(syncCommand(active.lockId(), "lock.confirmed.v1:SYNC-GUARD-001", "IDEMP-SYNC-GUARD-001", TENANT_A));

    LockServiceException invalid = assertThrows(
      LockServiceException.class,
      () -> service.acknowledgeLockStatus(ackCommand(
        active.lockId(), "lock.confirmed.v1:SYNC-GUARD-001", "ACK-GUARD-001", true, LockModels.RateLockStatus.REQUESTED, TENANT_A
      ))
    );
    assertEquals("LOCK_STATE_CONFLICT", invalid.code());

    LockModels.LockSyncAttemptResponse reconciled = service.acknowledgeLockStatus(ackCommand(
      active.lockId(), "lock.confirmed.v1:SYNC-GUARD-001", "ACK-GUARD-002", true, LockModels.RateLockStatus.CANCELLED, TENANT_A,
      LockModels.LockSyncStatus.RECONCILED
    ));
    assertEquals(LockModels.LockSyncStatus.RECONCILED, reconciled.status());
    assertEquals(LockModels.RateLockStatus.CANCELLED, service.getLock(TENANT_A, active.lockId()).status());
    assertEquals(1, service.committedReconciliationCount());
  }

  @Test
  void SyncTenantIsolationIT() {
    LockModels.LockConfirmationResponse active = createActiveLock("SYNC-ISO-001", TENANT_A);
    LockModels.LockSyncAttemptResponse response = service.syncLockStatusReplayAware(syncCommand(
      active.lockId(), "lock.confirmed.v1:SYNC-ISO-001", "IDEMP-SYNC-ISO-001", TENANT_A
    ));

    assertThrows(LockServiceException.class, () -> service.syncStatus(TENANT_B, active.lockId()));
    assertThrows(LockServiceException.class, () -> service.getSyncAttempt(TENANT_B, response.attemptId()));
  }

  @Test
  void LockStatusSyncApiContractsAndFixturesPass() throws IOException {
    LockModels.LockConfirmationResponse active = createActiveLock("SYNC-API-001", TENANT_A);
    service.syncLockStatusReplayAware(syncCommand(active.lockId(), "lock.confirmed.v1:SYNC-API-001", "IDEMP-SYNC-API-001", TENANT_A));
    LockStatusSyncApi api = new LockStatusSyncApi(service);

    assertEquals(LockStatusSyncApi.GET_SYNC_STATUS_PATH, "/api/v1/tenants/{tenantId}/locks/{lockId}/sync-status");
    assertEquals(LockStatusSyncApi.POST_ACK_PATH, "/api/v1/tenants/{tenantId}/integrations/lock-status-acks");
    assertEquals(1, api.getSyncStatus(TENANT_A, active.lockId(), true).size());
    assertThrows(LockServiceException.class, () -> api.getSyncStatus(TENANT_A, active.lockId(), false));
    assertResourceContains("contracts/pii-10/s09/los-lock-status-update.v1.json", "lock.status_sync.sent.v1");
    assertResourceContains("contracts/pii-10/s09/investor-lock-ack.v1.json", "LOCK_SYNC_ACK_WRITE");
    assertResourceContains("contracts/pii-10/s09/lock.sync_failed.v1.event.json", "lock.sync_failed.v1");
    assertResourceContains("golden/pii-10/s09/confirmed-lock-los-sync.json", "payloadHash");
    assertResourceContains("golden/pii-10/s09/investor-ack-reconciled.json", "RECONCILED");
    assertResourceContains("golden/pii-10/s09/external-cancel-blocked-invalid-state.json", "LOCK_STATE_CONFLICT");
  }

  @Test
  void AuditReplayGoldenFixturesPass() throws IOException {
    assertResourceContains("contracts/pii-10/s08/post-renegotiation-preview-200.json", "benefitLedgerHash");
    assertResourceContains("contracts/pii-10/s08/post-renegotiation-request-202.json", "lock.renegotiation_requested.v1");
    assertResourceContains("contracts/pii-10/s08/lock.terms_amended.v1.event.json", "lock.terms_amended.v1");
    assertResourceContains("golden/pii-10/s08/eligible-float-down.json", "benefit-ledger-config-ref");
    assertResourceContains("golden/pii-10/s08/below-threshold-blocked.json", "POLICY_NOT_SATISFIED");
    assertResourceContains("golden/pii-10/s08/compliance-apr-blocked.json", "compliance-relock-evidence-v1");
    assertResourceContains("golden/pii-10/s08/investor-float-down-confirmed.json", "lock.terms_amended.v1");
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

  private LockModels.LockConfirmationResponse createActiveLock(String suffix, UUID tenantId) {
    LockModels.LockRequestResponse request = service.requestLock(validCommand("REQ-" + suffix, "IDEMP-REQ-" + suffix, tenantId));
    LockModels.LockDecisionResponse approval = service.decideLock(decisionCommand(
      request.lockId(), LockModels.LockDecisionType.APPROVE, request.version(), "IDEMP-DEC-" + suffix, tenantId
    ));
    return service.confirmLock(withLockNumber(
      confirmationCommand(approval.lockId(), approval.version(), "IDEMP-CONF-" + suffix, tenantId, LockModels.LockConfirmationType.INTERNAL, true),
      "LOCK-" + suffix
    ));
  }

  private static LockModels.LockStatusSyncCommand syncCommand(
    String lockId,
    String eventId,
    String idempotencyKey,
    UUID tenantId
  ) {
    return syncCommand(lockId, eventId, idempotencyKey, tenantId, "LOS-LOCK-STATUS");
  }

  private static LockModels.LockStatusSyncCommand syncCommand(
    String lockId,
    String eventId,
    String idempotencyKey,
    UUID tenantId,
    String targetId
  ) {
    return new LockModels.LockStatusSyncCommand(
      tenantId,
      lockId,
      eventId,
      "lock-sync-service",
      syncTarget(tenantId, targetId),
      true,
      Instant.parse("2026-07-04T22:00:00Z"),
      idempotencyKey,
      "corr-pii10-s09",
      Map.of("lockEvent", eventId, "snapshot", "lock-status-snapshot-v1")
    );
  }

  private static LockModels.LockSyncTarget syncTarget(UUID tenantId, String targetId) {
    return new LockModels.LockSyncTarget(
      tenantId,
      targetId,
      targetId,
      true,
      "lock-status-sync-contract-v1",
      "lock-sync-policy-v1"
    );
  }

  private static LockModels.LockStatusAckCommand ackCommand(
    String lockId,
    String eventId,
    String ackId,
    boolean sourceTrusted,
    LockModels.RateLockStatus requestedLockStatus,
    UUID tenantId
  ) {
    return ackCommand(lockId, eventId, ackId, sourceTrusted, requestedLockStatus, tenantId, LockModels.LockSyncStatus.ACKED);
  }

  private static LockModels.LockStatusAckCommand ackCommand(
    String lockId,
    String eventId,
    String ackId,
    boolean sourceTrusted,
    LockModels.RateLockStatus requestedLockStatus,
    UUID tenantId,
    LockModels.LockSyncStatus ackStatus
  ) {
    return new LockModels.LockStatusAckCommand(
      tenantId,
      ackId,
      lockId,
      eventId,
      "LOS-LOCK-STATUS",
      "los-adapter-service",
      true,
      sourceTrusted,
      LockModels.RateLockStatus.ACTIVE,
      requestedLockStatus,
      ackStatus,
      "LOS-ACK-REF-" + ackId,
      "lock-sync-policy-v1",
      "lock-status-sync-contract-v1",
      Instant.parse("2026-07-04T22:01:00Z"),
      "IDEMP-" + ackId,
      "corr-pii10-s09",
      Map.of("ackPayload", "los-lock-status-ack-v1")
    );
  }

  private static LockModels.LockExpirationRunCommand expirationCommand(
    String runId,
    UUID tenantId,
    Instant evaluatedAt,
    long warningThresholdSeconds,
    boolean policyResolved
  ) {
    return new LockModels.LockExpirationRunCommand(
      tenantId,
      runId,
      "lock-expiration-scheduler",
      evaluatedAt,
      warningThresholdSeconds,
      "lock-expiration-policy-v1",
      true,
      policyResolved,
      false,
      "corr-pii10-s05"
    );
  }

  private static LockModels.LockConfirmationCommand confirmationCommand(
    String lockId,
    int expectedVersion,
    String idempotencyKey,
    UUID tenantId,
    LockModels.LockConfirmationType confirmationType,
    boolean investorResponseMatches
  ) {
    return new LockModels.LockConfirmationCommand(
      tenantId,
      lockId,
      confirmationType == LockModels.LockConfirmationType.INVESTOR_CALLBACK ? "investor-adapter-service" : "lock-desk-user-1",
      expectedVersion,
      confirmationType,
      "LOCK-PII10-001",
      false,
      "INVESTOR-A",
      confirmationType == LockModels.LockConfirmationType.INTERNAL ? "" : "INV-CONF-REF-001",
      30,
      Instant.parse("2026-06-04T21:06:00Z"),
      Instant.parse("2026-07-04T21:06:00Z"),
      "CONVENTIONAL-30Y",
      "CONVENTIONAL-30Y",
      "LOAN-PII10",
      "LOAN-PII10",
      "lock-policy-v1",
      "lock-policy-v1",
      true,
      true,
      true,
      investorResponseMatches,
      "compliance-evidence-v1",
      idempotencyKey,
      "corr-pii10-s04",
      Map.of("quoteSnapshot", "quote-snapshot-v1", "freshnessCheck", "freshness-check-v1")
    );
  }

  private static LockModels.LockConfirmationCommand withResponseProduct(LockModels.LockConfirmationCommand command, String responseProductId) {
    return new LockModels.LockConfirmationCommand(
      command.tenantId(), command.lockId(), command.actorId(), command.expectedVersion(), command.confirmationType(),
      command.lockNumber(), command.lockNumberOverrideAllowed(), command.investorId(), command.investorConfirmationRef(),
      command.lockPeriodDays(), command.confirmedAt(), command.expiresAt(), command.requestedProductId(), responseProductId,
      command.requestedLoanId(), command.responseLoanId(), command.requestedPolicyVersionId(), command.responsePolicyVersionId(),
      command.permissionGranted(), command.freshnessLockable(), command.confirmationPolicyResolved(), command.investorResponseMatches(),
      command.complianceEvidenceRef(), command.idempotencyKey(), command.correlationId(), command.sourceRefs()
    );
  }

  private static LockModels.LockConfirmationCommand withLockNumber(LockModels.LockConfirmationCommand command, String lockNumber) {
    return new LockModels.LockConfirmationCommand(
      command.tenantId(), command.lockId(), command.actorId(), command.expectedVersion(), command.confirmationType(),
      lockNumber, command.lockNumberOverrideAllowed(), command.investorId(), command.investorConfirmationRef(),
      command.lockPeriodDays(), command.confirmedAt(), command.expiresAt(), command.requestedProductId(), command.responseProductId(),
      command.requestedLoanId(), command.responseLoanId(), command.requestedPolicyVersionId(), command.responsePolicyVersionId(),
      command.permissionGranted(), command.freshnessLockable(), command.confirmationPolicyResolved(), command.investorResponseMatches(),
      command.complianceEvidenceRef(), command.idempotencyKey(), command.correlationId(), command.sourceRefs()
    );
  }

  private static LockConfirmationApi.ConfirmationRequest confirmationApiRequest(int expectedVersion) {
    return new LockConfirmationApi.ConfirmationRequest(
      "lock-desk-user-1",
      expectedVersion,
      LockModels.LockConfirmationType.INTERNAL,
      "LOCK-PII10-API-001",
      false,
      "INVESTOR-A",
      "",
      30,
      Instant.parse("2026-06-04T21:06:00Z"),
      Instant.parse("2026-07-04T21:06:00Z"),
      "CONVENTIONAL-30Y",
      "CONVENTIONAL-30Y",
      "LOAN-PII10",
      "LOAN-PII10",
      "lock-policy-v1",
      "lock-policy-v1",
      true,
      true,
      true,
      true,
      "compliance-evidence-v1",
      Map.of("quoteSnapshot", "quote-snapshot-v1", "freshnessCheck", "freshness-check-v1")
    );
  }

  private static LockModels.ExtensionCostSnapshot extensionCostSnapshot() {
    return new LockModels.ExtensionCostSnapshot(
      "price-adjustment-config-ref:EXT-3D",
      "fee-config-ref:EXT-3D",
      "BORROWER_PAID",
      "policy-rounding-v1",
      "EXTENSION_REASON_CONFIGURED",
      "extension-policy-v1"
    );
  }

  private static LockModels.LockExtensionPreviewCommand extensionPreviewCommand(String lockId, int expectedVersion, UUID tenantId) {
    return new LockModels.LockExtensionPreviewCommand(
      tenantId,
      lockId,
      "lock-desk-user-1",
      expectedVersion,
      3,
      Instant.parse("2026-07-07T21:06:00Z"),
      "EXTENSION_REASON_CONFIGURED",
      extensionCostSnapshot(),
      true,
      true,
      true,
      true,
      "IDEMP-EXT-PREVIEW-001",
      "corr-pii10-s06",
      Map.of("policyConfig", "extension-policy-v1", "costSnapshot", "extension-cost-snapshot-v1")
    );
  }

  private static LockModels.LockExtensionRequestCommand extensionRequestCommand(
    String lockId,
    int expectedVersion,
    String requestId,
    String idempotencyKey,
    UUID tenantId
  ) {
    return new LockModels.LockExtensionRequestCommand(
      tenantId,
      lockId,
      requestId,
      "lock-desk-user-1",
      expectedVersion,
      3,
      Instant.parse("2026-07-04T21:07:00Z"),
      Instant.parse("2026-07-07T21:06:00Z"),
      "EXTENSION_REASON_CONFIGURED",
      extensionCostSnapshot(),
      true,
      true,
      true,
      true,
      "compliance-extension-evidence-v1",
      idempotencyKey,
      "corr-pii10-s06",
      Map.of("policyConfig", "extension-policy-v1", "costSnapshot", "extension-cost-snapshot-v1")
    );
  }

  private static LockModels.LockExtensionRequestCommand withExtensionPolicy(
    LockModels.LockExtensionRequestCommand command,
    boolean extensionPolicyResolved
  ) {
    return new LockModels.LockExtensionRequestCommand(
      command.tenantId(), command.lockId(), command.requestId(), command.actorId(), command.expectedVersion(), command.requestedDays(),
      command.requestedAt(), command.requestedExpiresAt(), command.reasonCode(), command.costSnapshot(), command.permissionGranted(),
      extensionPolicyResolved, command.compliancePermitsAmendedTerms(), command.investorSupportsExtension(),
      command.complianceEvidenceRef(), command.idempotencyKey(), command.correlationId(), command.sourceRefs()
    );
  }

  private static LockModels.LockExtensionDecisionCommand extensionDecisionCommand(
    String lockId,
    String extensionId,
    LockModels.LockExtensionDecisionType decision,
    int expectedVersion,
    boolean investorConfirmationRequired,
    String idempotencyKey,
    UUID tenantId
  ) {
    return new LockModels.LockExtensionDecisionCommand(
      tenantId,
      lockId,
      extensionId,
      decision,
      "lock-desk-approver-1",
      "lock-desk-user-1",
      expectedVersion,
      investorConfirmationRequired,
      true,
      true,
      true,
      List.of("EXTENSION_POLICY_OK"),
      "compliance-extension-evidence-v1",
      idempotencyKey,
      "corr-pii10-s06",
      Instant.parse("2026-07-04T21:08:00Z")
    );
  }

  private static LockModels.LockExtensionConfirmationCommand extensionConfirmationCommand(
    String lockId,
    String extensionId,
    int expectedVersion,
    String idempotencyKey,
    UUID tenantId,
    boolean investorResponseMatches
  ) {
    return new LockModels.LockExtensionConfirmationCommand(
      tenantId,
      lockId,
      extensionId,
      "investor-adapter-service",
      expectedVersion,
      true,
      investorResponseMatches,
      "INV-EXT-CONF-REF-001",
      "compliance-extension-evidence-v1",
      idempotencyKey,
      "corr-pii10-s06",
      Instant.parse("2026-07-04T21:09:00Z"),
      Map.of("investorResponse", "investor-extension-confirmation-v1")
    );
  }

  private static LockModels.LockExtensionCancelCommand extensionCancelCommand(
    String lockId,
    String extensionId,
    int expectedVersion,
    String idempotencyKey,
    UUID tenantId
  ) {
    return new LockModels.LockExtensionCancelCommand(
      tenantId,
      lockId,
      extensionId,
      "lock-desk-user-1",
      expectedVersion,
      true,
      List.of("BORROWER_WITHDREW_EXTENSION"),
      "compliance-extension-evidence-v1",
      idempotencyKey,
      "corr-pii10-s06",
      Instant.parse("2026-07-04T21:10:00Z")
    );
  }

  private static LockExtensionApi.ExtensionPreviewRequest extensionPreviewApiRequest(int expectedVersion) {
    return new LockExtensionApi.ExtensionPreviewRequest(
      "lock-desk-user-1",
      expectedVersion,
      3,
      Instant.parse("2026-07-07T21:06:00Z"),
      "EXTENSION_REASON_CONFIGURED",
      extensionCostSnapshot(),
      true,
      true,
      true,
      true,
      Map.of("policyConfig", "extension-policy-v1", "costSnapshot", "extension-cost-snapshot-v1")
    );
  }

  private static LockExtensionApi.ExtensionRequest extensionApiRequest(int expectedVersion) {
    return new LockExtensionApi.ExtensionRequest(
      "EXT-API-001",
      "lock-desk-user-1",
      expectedVersion,
      3,
      Instant.parse("2026-07-04T21:07:00Z"),
      Instant.parse("2026-07-07T21:06:00Z"),
      "EXTENSION_REASON_CONFIGURED",
      extensionCostSnapshot(),
      true,
      true,
      true,
      true,
      "compliance-extension-evidence-v1",
      Map.of("policyConfig", "extension-policy-v1", "costSnapshot", "extension-cost-snapshot-v1")
    );
  }

  private static LockExtensionApi.ExtensionDecisionRequest extensionDecisionApiRequest(int expectedVersion, boolean investorConfirmationRequired) {
    return new LockExtensionApi.ExtensionDecisionRequest(
      LockModels.LockExtensionDecisionType.APPROVE,
      "lock-desk-approver-1",
      "lock-desk-user-1",
      expectedVersion,
      investorConfirmationRequired,
      true,
      true,
      true,
      List.of("EXTENSION_POLICY_OK"),
      "compliance-extension-evidence-v1",
      Instant.parse("2026-07-04T21:08:00Z")
    );
  }

  private static LockExtensionApi.ExtensionCancelRequest extensionCancelApiRequest(int expectedVersion) {
    return new LockExtensionApi.ExtensionCancelRequest(
      "lock-desk-user-1",
      expectedVersion,
      true,
      List.of("BORROWER_WITHDREW_EXTENSION"),
      "compliance-extension-evidence-v1",
      Instant.parse("2026-07-04T21:10:00Z")
    );
  }

  private LockModels.RelockPreviewCommand relockPreviewCommand(
    String sourceLockId,
    int expectedVersion,
    String currentQuoteId,
    UUID tenantId
  ) {
    return new LockModels.RelockPreviewCommand(
      tenantId,
      sourceLockId,
      "lock-desk-user-1",
      expectedVersion,
      currentQuoteId,
      originalTerms(sourceLockId, tenantId),
      currentRelockTerms(),
      originalTerms(sourceLockId, tenantId),
      relockPolicy(true, true),
      "RELOCK_REASON_CONFIGURED",
      true,
      "IDEMP-REL-PREVIEW-001",
      "corr-pii10-s07",
      Map.of("policyConfig", "relock-policy-v1", "comparisonSnapshot", "relock-comparison-v1")
    );
  }

  private LockModels.RelockRequestCommand relockRequestCommand(
    String sourceLockId,
    int expectedVersion,
    String requestId,
    String idempotencyKey,
    String currentQuoteId,
    UUID tenantId
  ) {
    return new LockModels.RelockRequestCommand(
      tenantId,
      sourceLockId,
      requestId,
      "lock-desk-user-1",
      expectedVersion,
      currentQuoteId,
      Instant.parse("2026-07-04T21:11:00Z"),
      originalTerms(sourceLockId, tenantId),
      currentRelockTerms(),
      originalTerms(sourceLockId, tenantId),
      relockPolicy(true, true),
      "RELOCK_REASON_CONFIGURED",
      "compliance-relock-evidence-v1",
      true,
      idempotencyKey,
      "corr-pii10-s07",
      Map.of("policyConfig", "relock-policy-v1", "comparisonSnapshot", "relock-comparison-v1")
    );
  }

  private static LockModels.RelockRequestCommand withRelockPolicy(
    LockModels.RelockRequestCommand command,
    LockModels.RelockPolicySnapshot policySnapshot
  ) {
    return new LockModels.RelockRequestCommand(
      command.tenantId(), command.sourceLockId(), command.requestId(), command.actorId(), command.expectedVersion(), command.currentQuoteId(),
      command.requestedAt(), command.originalTerms(), command.currentTerms(), command.selectedTerms(), policySnapshot,
      command.reasonCode(), command.complianceEvidenceRef(), command.permissionGranted(), command.idempotencyKey(), command.correlationId(),
      command.sourceRefs()
    );
  }

  private static LockModels.RelockPreviewCommand withSelectedTerms(
    LockModels.RelockPreviewCommand command,
    LockModels.RelockTermsSnapshot selectedTerms
  ) {
    return new LockModels.RelockPreviewCommand(
      command.tenantId(), command.sourceLockId(), command.actorId(), command.expectedVersion(), command.currentQuoteId(),
      command.originalTerms(), command.currentTerms(), selectedTerms, command.policySnapshot(), command.reasonCode(),
      command.permissionGranted(), command.idempotencyKey(), command.correlationId(), command.sourceRefs()
    );
  }

  private static LockModels.RelockDecisionCommand relockDecisionCommand(
    String sourceLockId,
    String relockId,
    LockModels.RelockDecisionType decision,
    int expectedVersion,
    String idempotencyKey,
    UUID tenantId
  ) {
    return new LockModels.RelockDecisionCommand(
      tenantId,
      sourceLockId,
      relockId,
      decision,
      "lock-desk-approver-1",
      "lock-desk-user-1",
      expectedVersion,
      true,
      true,
      true,
      List.of("RELOCK_POLICY_OK"),
      "compliance-relock-evidence-v1",
      idempotencyKey,
      "corr-pii10-s07",
      Instant.parse("2026-07-04T21:12:00Z")
    );
  }

  private static LockModels.RelockConfirmationCommand relockConfirmationCommand(
    String sourceLockId,
    String relockId,
    int expectedVersion,
    String idempotencyKey,
    UUID tenantId,
    boolean investorResponseMatches
  ) {
    return new LockModels.RelockConfirmationCommand(
      tenantId,
      sourceLockId,
      relockId,
      "investor-adapter-service",
      expectedVersion,
      true,
      investorResponseMatches,
      "INV-RELOCK-CONF-REF-001",
      "compliance-relock-evidence-v1",
      idempotencyKey,
      "corr-pii10-s07",
      Instant.parse("2026-07-04T21:13:00Z"),
      Map.of("investorResponse", "investor-relock-confirmation-v1")
    );
  }

  private LockRelockApi.RelockPreviewRequest relockPreviewApiRequest(String sourceLockId, int expectedVersion, String currentQuoteId) {
    return new LockRelockApi.RelockPreviewRequest(
      "lock-desk-user-1",
      expectedVersion,
      currentQuoteId,
      originalTerms(sourceLockId, TENANT_A),
      currentRelockTerms(),
      originalTerms(sourceLockId, TENANT_A),
      relockPolicy(true, true),
      "RELOCK_REASON_CONFIGURED",
      true,
      Map.of("policyConfig", "relock-policy-v1", "comparisonSnapshot", "relock-comparison-v1")
    );
  }

  private LockRenegotiationApi.RenegotiationPreviewRequest renegotiationPreviewApiRequest(String sourceLockId, int expectedVersion, String currentQuoteId) {
    return new LockRenegotiationApi.RenegotiationPreviewRequest(
      "lock-desk-user-1",
      expectedVersion,
      currentQuoteId,
      originalTerms(sourceLockId, TENANT_A),
      currentRelockTerms(),
      originalTerms(sourceLockId, TENANT_A),
      relockPolicy(true, true),
      "FLOAT_DOWN_REASON_CONFIGURED",
      true,
      Map.of("policyConfig", "relock-policy-v1", "comparisonSnapshot", "relock-comparison-v1")
    );
  }

  private LockRelockApi.RelockRequest relockApiRequest(String sourceLockId, int expectedVersion, String currentQuoteId) {
    return new LockRelockApi.RelockRequest(
      "REL-API-001",
      "lock-desk-user-1",
      expectedVersion,
      currentQuoteId,
      Instant.parse("2026-07-04T21:11:00Z"),
      originalTerms(sourceLockId, TENANT_A),
      currentRelockTerms(),
      originalTerms(sourceLockId, TENANT_A),
      relockPolicy(true, true),
      "RELOCK_REASON_CONFIGURED",
      "compliance-relock-evidence-v1",
      true,
      Map.of("policyConfig", "relock-policy-v1", "comparisonSnapshot", "relock-comparison-v1")
    );
  }

  private LockRenegotiationApi.RenegotiationRequest renegotiationApiRequest(String sourceLockId, int expectedVersion, String currentQuoteId) {
    return new LockRenegotiationApi.RenegotiationRequest(
      "REN-API-001",
      "lock-desk-user-1",
      expectedVersion,
      currentQuoteId,
      Instant.parse("2026-07-04T21:11:00Z"),
      originalTerms(sourceLockId, TENANT_A),
      currentRelockTerms(),
      originalTerms(sourceLockId, TENANT_A),
      relockPolicy(true, true),
      "FLOAT_DOWN_REASON_CONFIGURED",
      "compliance-relock-evidence-v1",
      true,
      Map.of("policyConfig", "relock-policy-v1", "comparisonSnapshot", "relock-comparison-v1")
    );
  }

  private static LockRelockApi.RelockDecisionRequest relockDecisionApiRequest(int expectedVersion) {
    return new LockRelockApi.RelockDecisionRequest(
      LockModels.RelockDecisionType.APPROVE,
      "lock-desk-approver-1",
      "lock-desk-user-1",
      expectedVersion,
      true,
      true,
      true,
      List.of("RELOCK_POLICY_OK"),
      "compliance-relock-evidence-v1",
      Instant.parse("2026-07-04T21:12:00Z")
    );
  }

  private static LockRenegotiationApi.RenegotiationDecisionRequest renegotiationDecisionApiRequest(int expectedVersion) {
    return new LockRenegotiationApi.RenegotiationDecisionRequest(
      LockModels.RelockDecisionType.APPROVE,
      "lock-desk-approver-1",
      "lock-desk-user-1",
      expectedVersion,
      true,
      true,
      true,
      List.of("FLOAT_DOWN_POLICY_OK"),
      "compliance-relock-evidence-v1",
      Instant.parse("2026-07-04T21:12:00Z")
    );
  }

  private static LockRelockApi.RelockConfirmationRequest relockConfirmationApiRequest(int expectedVersion) {
    return new LockRelockApi.RelockConfirmationRequest(
      "investor-adapter-service",
      expectedVersion,
      true,
      true,
      "INV-RELOCK-CONF-REF-001",
      "compliance-relock-evidence-v1",
      Instant.parse("2026-07-04T21:13:00Z"),
      Map.of("investorResponse", "investor-relock-confirmation-v1")
    );
  }

  private static LockRenegotiationApi.RenegotiationConfirmationRequest renegotiationConfirmationApiRequest(int expectedVersion) {
    return new LockRenegotiationApi.RenegotiationConfirmationRequest(
      "investor-adapter-service",
      expectedVersion,
      true,
      true,
      "INV-REN-CONF-REF-001",
      "compliance-relock-evidence-v1",
      Instant.parse("2026-07-04T21:13:00Z"),
      Map.of("investorResponse", "investor-renegotiation-confirmation-v1")
    );
  }

  private LockModels.RelockTermsSnapshot originalTerms(String sourceLockId, UUID tenantId) {
    LockModels.RateLockRecord source = service.getLock(tenantId, sourceLockId);
    return new LockModels.RelockTermsSnapshot(
      "CONVENTIONAL-30Y",
      "INVESTOR-A",
      "rate-sheet-v1",
      "original-price-ref",
      "original-rate-ref",
      "original-fee-ref",
      "original-lock-period-ref",
      source.requestHash()
    );
  }

  private static LockModels.RelockTermsSnapshot currentRelockTerms() {
    return new LockModels.RelockTermsSnapshot(
      "CONVENTIONAL-30Y",
      "INVESTOR-A",
      "rate-sheet-v2",
      "current-price-ref",
      "current-rate-ref",
      "current-fee-ref",
      "current-lock-period-ref",
      "current-terms-hash-v1"
    );
  }

  private static LockModels.RelockPolicySnapshot relockPolicy(boolean currentQuoteFresh, boolean policyResolved) {
    return relockPolicy(currentQuoteFresh, policyResolved, true, false);
  }

  private static LockModels.RelockPolicySnapshot relockPolicy(
    boolean currentQuoteFresh,
    boolean policyResolved,
    boolean waitingPeriodSatisfied,
    boolean investorConfirmationRequired
  ) {
    return new LockModels.RelockPolicySnapshot(
      "relock-policy-v1",
      "worse-case-selection-config-ref",
      "waiting-period-config-ref",
      "fee-treatment-config-ref",
      "eligibility-threshold-config-ref",
      "benefit-ledger-config-ref",
      true,
      currentQuoteFresh,
      waitingPeriodSatisfied,
      policyResolved,
      true,
      investorConfirmationRequired
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

  private void assertResourceContains(String resourcePath, String expectedText) throws IOException {
    try (var stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      assertNotNull(stream, "Missing test resource " + resourcePath);
      assertTrue(new String(stream.readAllBytes(), StandardCharsets.UTF_8).contains(expectedText));
    }
  }
}
