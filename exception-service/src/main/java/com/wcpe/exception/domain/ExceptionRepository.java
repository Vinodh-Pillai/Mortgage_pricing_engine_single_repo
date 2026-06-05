package com.wcpe.exception.domain;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory repository for exception requests.
 * Mock-backed only; no persistence in this PII.
 */
public class ExceptionRepository {

  private final ConcurrentHashMap<String, ExceptionModels.ExceptionRequestRecord> store = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ExceptionModels.PricingConcessionRequestRecord> concessionStore = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> concessionIdempotencyIndex = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ExceptionModels.ApprovalDecisionRecord> approvalStore = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> approvalIdempotencyIndex = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ExceptionModels.ConcessionApplicationRecord> applicationStore = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> applicationIdempotencyIndex = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> applicationTargetIndex = new ConcurrentHashMap<>();
  private final AtomicLong sequence = new AtomicLong(0);
  private final AtomicLong concessionSequence = new AtomicLong(0);
  private final AtomicLong approvalSequence = new AtomicLong(0);
  private final AtomicLong applicationSequence = new AtomicLong(0);

  public ExceptionModels.ExceptionRequestRecord create(ExceptionModels.ExceptionRequestCreate request) {
    String id = "EXC-" + sequence.incrementAndGet();
    Instant now = Instant.now();
    ExceptionModels.ExceptionRequestRecord record = new ExceptionModels.ExceptionRequestRecord(
      id,
      request.placeholderQuoteReference(),
      request.requestType(),
      ExceptionModels.ExceptionState.DRAFT,
      now,
      now
    );
    store.put(id, record);
    return record;
  }

  public Optional<ExceptionModels.ExceptionRequestRecord> findById(String id) {
    return Optional.ofNullable(store.get(id));
  }

  public Optional<ExceptionModels.PricingConcessionRequestRecord> findConcessionById(String tenantId, String id) {
    ExceptionModels.PricingConcessionRequestRecord record = concessionStore.get(id);
    if (record == null || !record.tenantId().toString().equals(tenantId)) {
      return Optional.empty();
    }
    return Optional.of(record);
  }

  public Optional<ExceptionModels.PricingConcessionRequestRecord> findConcessionByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    String requestId = concessionIdempotencyIndex.get(concessionIdempotencyIndexKey(tenantId, idempotencyKey));
    if (requestId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(concessionStore.get(requestId));
  }

  public ExceptionModels.PricingConcessionRequestRecord createConcession(
    ExceptionModels.PricingConcessionRequestCreate request,
    ExceptionModels.ConcessionRequestStatus status,
    String commentsRedacted,
    String approvalRouteHash,
    String requestHash
  ) {
    String id = "PCR-" + concessionSequence.incrementAndGet();
    Instant now = Instant.now();
    ExceptionModels.ApprovalRouteSnapshot route = request.approvalRouteSnapshot();
    ExceptionModels.PricingConcessionRequestRecord record = new ExceptionModels.PricingConcessionRequestRecord(
      request.tenantId(),
      id,
      request.quoteId(),
      request.scenarioId(),
      request.lockId(),
      status,
      request.requestedAmount(),
      request.reasonCode(),
      commentsRedacted,
      List.copyOf(request.evidenceRefs()),
      request.expiration(),
      request.concessionPolicyVersionId(),
      route.authorityMatrixVersionId(),
      request.reasonCodeVersionId(),
      request.quoteSnapshotHash(),
      approvalRouteHash,
      List.copyOf(route.approverGroups()),
      route.sla(),
      request.idempotencyKey(),
      request.actorId(),
      request.correlationId(),
      "AUDIT-" + id,
      "pricing.concession.requested.v1",
      requestHash,
      1,
      now,
      now
    );
    concessionStore.put(id, record);
    concessionIdempotencyIndex.put(concessionIdempotencyIndexKey(request.tenantId(), request.idempotencyKey()), id);
    return record;
  }

  public Optional<ExceptionModels.ApprovalDecisionRecord> findApprovalByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    String decisionId = approvalIdempotencyIndex.get(approvalIdempotencyIndexKey(tenantId, idempotencyKey));
    if (decisionId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(approvalStore.get(decisionId));
  }

  public ExceptionModels.ApprovalDecisionRecord approveConcession(
    ExceptionModels.PricingConcessionRequestRecord request,
    ExceptionModels.ApproveConcessionRequest approval,
    String commentRedacted,
    String eventHash
  ) {
    String decisionId = "APD-" + approvalSequence.incrementAndGet();
    Instant now = Instant.now();
    int nextVersion = request.version() + 1;
    ExceptionModels.ApprovalDecisionRecord decision = new ExceptionModels.ApprovalDecisionRecord(
      request.tenantId(),
      decisionId,
      request.concessionRequestId(),
      approval.routeStepId(),
      approval.decision(),
      approval.reasonCode(),
      commentRedacted,
      approval.conditions(),
      approval.authorityMatrixVersionId(),
      approval.actorId(),
      List.copyOf(approval.actorRoleRefs()),
      approval.conflictAttestation(),
      approval.idempotencyKey(),
      approval.correlationId(),
      "AUDIT-" + decisionId,
      "concession.request.approved.v1",
      eventHash,
      nextVersion,
      now
    );
    approvalStore.put(decisionId, decision);
    approvalIdempotencyIndex.put(approvalIdempotencyIndexKey(request.tenantId(), approval.idempotencyKey()), decisionId);
    concessionStore.put(request.concessionRequestId(), new ExceptionModels.PricingConcessionRequestRecord(
      request.tenantId(),
      request.concessionRequestId(),
      request.quoteId(),
      request.scenarioId(),
      request.lockId(),
      ExceptionModels.ConcessionRequestStatus.APPROVED_PENDING_APPLICATION,
      request.requestedAmount(),
      request.reasonCode(),
      request.commentsRedacted(),
      request.evidenceRefs(),
      request.expiration(),
      request.concessionPolicyVersionId(),
      request.authorityMatrixVersionId(),
      request.reasonCodeVersionId(),
      request.quoteSnapshotHash(),
      request.approvalRouteHash(),
      request.nextApproverGroups(),
      request.sla(),
      request.idempotencyKey(),
      request.actorId(),
      request.correlationId(),
      request.auditRef(),
      request.outboxEventType(),
      request.requestHash(),
      nextVersion,
      request.createdAt(),
      now
    ));
    return decision;
  }

  public Optional<ExceptionModels.ConcessionApplicationRecord> findApplicationByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    String applicationId = applicationIdempotencyIndex.get(applicationIdempotencyIndexKey(tenantId, idempotencyKey));
    if (applicationId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(applicationStore.get(applicationId));
  }

  public Optional<ExceptionModels.ConcessionApplicationRecord> findApplicationByTarget(
    UUID tenantId,
    String concessionRequestId,
    ExceptionModels.ApplicationTarget target
  ) {
    String applicationId = applicationTargetIndex.get(applicationTargetIndexKey(tenantId, concessionRequestId, target));
    if (applicationId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(applicationStore.get(applicationId));
  }

  public ExceptionModels.ConcessionApplicationRecord applyConcession(
    ExceptionModels.PricingConcessionRequestRecord request,
    ExceptionModels.ApplyApprovedConcessionRequest command,
    String pricingLedgerEntryId,
    String afterPriceHash,
    String replayHash,
    String outboxEventType
  ) {
    String applicationId = "APP-" + applicationSequence.incrementAndGet();
    Instant now = Instant.now();
    ExceptionModels.ApplicationTarget target = command.target();
    ExceptionModels.ConcessionApplicationRecord record = new ExceptionModels.ConcessionApplicationRecord(
      request.tenantId(),
      applicationId,
      request.concessionRequestId(),
      target.targetType(),
      target.quoteId(),
      target.lockId(),
      request.requestedAmount(),
      pricingLedgerEntryId,
      command.expectedLedgerHash(),
      afterPriceHash,
      command.pricingRuleVersionId(),
      command.policyVersionId(),
      command.precedence().precedenceConfigVersionId(),
      command.precedence().scale(),
      command.precedence().roundingMode(),
      ExceptionModels.ConcessionRequestStatus.APPLIED,
      command.idempotencyKey(),
      command.actorId(),
      command.correlationId(),
      "AUDIT-" + applicationId,
      outboxEventType,
      replayHash,
      request.version() + 1,
      now
    );
    applicationStore.put(applicationId, record);
    applicationIdempotencyIndex.put(applicationIdempotencyIndexKey(request.tenantId(), command.idempotencyKey()), applicationId);
    applicationTargetIndex.put(applicationTargetIndexKey(request.tenantId(), request.concessionRequestId(), target), applicationId);
    concessionStore.put(request.concessionRequestId(), new ExceptionModels.PricingConcessionRequestRecord(
      request.tenantId(),
      request.concessionRequestId(),
      request.quoteId(),
      request.scenarioId(),
      request.lockId(),
      ExceptionModels.ConcessionRequestStatus.APPLIED,
      request.requestedAmount(),
      request.reasonCode(),
      request.commentsRedacted(),
      request.evidenceRefs(),
      request.expiration(),
      request.concessionPolicyVersionId(),
      request.authorityMatrixVersionId(),
      request.reasonCodeVersionId(),
      request.quoteSnapshotHash(),
      request.approvalRouteHash(),
      request.nextApproverGroups(),
      request.sla(),
      request.idempotencyKey(),
      request.actorId(),
      request.correlationId(),
      request.auditRef(),
      request.outboxEventType(),
      request.requestHash(),
      request.version() + 1,
      request.createdAt(),
      now
    ));
    return record;
  }

  public Optional<ExceptionModels.ExceptionRequestRecord> transition(String id, ExceptionModels.ExceptionState target) {
    ExceptionModels.ExceptionRequestRecord existing = store.get(id);
    if (existing == null) return Optional.empty();

    Set<ExceptionModels.ExceptionState> allowed = ExceptionModels.ExceptionState.allowedTransitions(existing.state());
    if (!allowed.contains(target)) {
      throw new ExceptionServiceException(
        "INVALID_TRANSITION",
        String.format("Cannot transition from %s to %s", existing.state(), target)
      );
    }

    Instant now = Instant.now();
    ExceptionModels.ExceptionRequestRecord updated = new ExceptionModels.ExceptionRequestRecord(
      existing.exceptionRequestId(),
      existing.placeholderQuoteReference(),
      existing.requestType(),
      target,
      existing.createdAt(),
      now
    );
    store.put(id, updated);
    return Optional.of(updated);
  }

  void clear() {
    store.clear();
    concessionStore.clear();
    concessionIdempotencyIndex.clear();
    approvalStore.clear();
    approvalIdempotencyIndex.clear();
    applicationStore.clear();
    applicationIdempotencyIndex.clear();
    applicationTargetIndex.clear();
    sequence.set(0);
    concessionSequence.set(0);
    approvalSequence.set(0);
    applicationSequence.set(0);
  }

  private static String concessionIdempotencyIndexKey(UUID tenantId, String idempotencyKey) {
    return tenantId + ":" + idempotencyKey;
  }

  private static String approvalIdempotencyIndexKey(UUID tenantId, String idempotencyKey) {
    return tenantId + ":" + idempotencyKey;
  }

  private static String applicationIdempotencyIndexKey(UUID tenantId, String idempotencyKey) {
    return tenantId + ":" + idempotencyKey;
  }

  private static String applicationTargetIndexKey(
    UUID tenantId,
    String concessionRequestId,
    ExceptionModels.ApplicationTarget target
  ) {
    return tenantId + ":" + concessionRequestId + ":" + target.targetType() + ":"
      + Objects.toString(target.quoteId(), "") + ":" + Objects.toString(target.lockId(), "");
  }
}
