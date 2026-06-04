package com.wcpe.lock;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
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
}
