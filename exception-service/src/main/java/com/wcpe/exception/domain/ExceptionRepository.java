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
  private final ConcurrentHashMap<String, ExceptionModels.MonitoringSignalRecord> monitoringSignalStore = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> monitoringSignalIdempotencyIndex = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ExceptionModels.MonitoringAlertRecord> monitoringAlertStore = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> monitoringAlertBySignalIndex = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ExceptionModels.RiskMonitoringEventRecord> riskMonitoringEventStore = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> riskMonitoringEventIdempotencyIndex = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> riskMonitoringEventSourceIndex = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ExceptionModels.AlertDispositionRecord> alertDispositionStore = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> alertDispositionIdempotencyIndex = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ExceptionModels.EligibilityExceptionRequestRecord> eligibilityExceptionStore = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> eligibilityExceptionIdempotencyIndex = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> activeEligibilityExceptionIndex = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ExceptionModels.AuthorityMatrixVersionRecord> authorityMatrixStore = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> authorityMatrixIdempotencyIndex = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ExceptionModels.ManualPriceEditAttemptRecord> manualPriceEditAttemptStore = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> manualPriceEditAttemptIdempotencyIndex = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ExceptionModels.ExceptionHistoryProjectionRecord> historyProjectionStore = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ExceptionModels.ExceptionHistoryReplayResult> historyReplayStore = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ExceptionModels.ExceptionHistoryExportPacket> historyExportStore = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ExceptionModels.ExceptionHistoryAuditRecord> historyAuditStore = new ConcurrentHashMap<>();
  private final AtomicLong sequence = new AtomicLong(0);
  private final AtomicLong concessionSequence = new AtomicLong(0);
  private final AtomicLong approvalSequence = new AtomicLong(0);
  private final AtomicLong applicationSequence = new AtomicLong(0);
  private final AtomicLong monitoringSignalSequence = new AtomicLong(0);
  private final AtomicLong monitoringAlertSequence = new AtomicLong(0);
  private final AtomicLong riskMonitoringEventSequence = new AtomicLong(0);
  private final AtomicLong alertDispositionSequence = new AtomicLong(0);
  private final AtomicLong eligibilityExceptionSequence = new AtomicLong(0);
  private final AtomicLong authorityMatrixSequence = new AtomicLong(0);
  private final AtomicLong manualPriceEditAttemptSequence = new AtomicLong(0);

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

  public Optional<ExceptionModels.EligibilityExceptionRequestRecord> findEligibilityExceptionById(
    UUID tenantId,
    String id
  ) {
    ExceptionModels.EligibilityExceptionRequestRecord record = eligibilityExceptionStore.get(id);
    if (record == null || !record.tenantId().equals(tenantId)) {
      return Optional.empty();
    }
    return Optional.of(record);
  }

  public Optional<ExceptionModels.EligibilityExceptionRequestRecord> findEligibilityExceptionByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    String requestId = eligibilityExceptionIdempotencyIndex.get(eligibilityExceptionIdempotencyIndexKey(tenantId, idempotencyKey));
    if (requestId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(eligibilityExceptionStore.get(requestId));
  }

  public Optional<ExceptionModels.EligibilityExceptionRequestRecord> findActiveEligibilityException(
    UUID tenantId,
    ExceptionModels.EligibilityFindingRef findingRef,
    ExceptionModels.EligibilityExceptionScope exceptionScope
  ) {
    String requestId = activeEligibilityExceptionIndex.get(activeEligibilityExceptionIndexKey(tenantId, findingRef, exceptionScope));
    if (requestId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(eligibilityExceptionStore.get(requestId));
  }

  public ExceptionModels.EligibilityExceptionRequestRecord createEligibilityException(
    ExceptionModels.CreateEligibilityExceptionRequest request,
    ExceptionModels.EligibilityExceptionPolicy policy,
    String narrativeRedacted,
    String approvalRouteHash,
    String requestHash,
    String eventHash
  ) {
    String id = "EER-" + eligibilityExceptionSequence.incrementAndGet();
    Instant now = Instant.now();
    ExceptionModels.EligibilityExceptionRequestRecord record = new ExceptionModels.EligibilityExceptionRequestRecord(
      request.tenantId(),
      id,
      request.quoteId(),
      request.scenarioId(),
      request.lockId(),
      request.findingRef(),
      request.exceptionScope(),
      request.reasonCode(),
      narrativeRedacted,
      List.copyOf(request.compensatingFactors()),
      List.copyOf(request.evidenceRefs()),
      request.desiredExpiration(),
      request.relatedConcessionRequestId(),
      ExceptionModels.EligibilityExceptionRequestStatus.SUBMITTED,
      policy.policyVersionId(),
      policy.authorityMatrixVersionId(),
      approvalRouteHash,
      request.findingRef().originalResultHash(),
      "AUDIT-" + id,
      "EligibilityExceptionRequestSubmitted.v1",
      eventHash,
      requestHash,
      request.idempotencyKey(),
      request.actorId(),
      request.correlationId(),
      1,
      now,
      now
    );
    eligibilityExceptionStore.put(id, record);
    eligibilityExceptionIdempotencyIndex.put(eligibilityExceptionIdempotencyIndexKey(request.tenantId(), request.idempotencyKey()), id);
    activeEligibilityExceptionIndex.put(activeEligibilityExceptionIndexKey(request.tenantId(), request.findingRef(), request.exceptionScope()), id);
    return record;
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

  public Optional<ExceptionModels.MonitoringSignalRecord> findMonitoringSignalByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    String signalId = monitoringSignalIdempotencyIndex.get(monitoringIdempotencyIndexKey(tenantId, idempotencyKey));
    if (signalId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(monitoringSignalStore.get(signalId));
  }

  public Optional<ExceptionModels.MonitoringAlertRecord> findMonitoringAlertBySignalId(String signalId) {
    String alertId = monitoringAlertBySignalIndex.get(signalId);
    if (alertId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(monitoringAlertStore.get(alertId));
  }

  public Optional<ExceptionModels.MonitoringAlertRecord> findMonitoringAlertById(UUID tenantId, String alertId) {
    ExceptionModels.MonitoringAlertRecord record = monitoringAlertStore.get(alertId);
    if (record == null || !record.tenantId().equals(tenantId)) {
      return Optional.empty();
    }
    return Optional.of(record);
  }

  public Optional<ExceptionModels.RiskMonitoringEventRecord> findRiskMonitoringEventByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    String eventId = riskMonitoringEventIdempotencyIndex.get(riskMonitoringIdempotencyIndexKey(tenantId, idempotencyKey));
    if (eventId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(riskMonitoringEventStore.get(eventId));
  }

  public Optional<ExceptionModels.RiskMonitoringEventRecord> findRiskMonitoringEventBySourceEvent(
    UUID tenantId,
    String sourceEventId
  ) {
    String eventId = riskMonitoringEventSourceIndex.get(riskMonitoringSourceIndexKey(tenantId, sourceEventId));
    if (eventId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(riskMonitoringEventStore.get(eventId));
  }

  public ExceptionModels.RiskMonitoringEventRecord recordRiskMonitoringEvent(
    ExceptionModels.MapRiskMonitoringEventCommand command,
    ExceptionModels.RiskEventMappingRule rule,
    ExceptionModels.RiskMonitoringEventStatus status,
    String topic,
    String eventKey,
    Map<String, String> headers,
    Map<String, String> payload,
    String payloadHash,
    Map<String, String> redactionManifest,
    String mappingVersionId,
    boolean replayFlag,
    String requestHash
  ) {
    String riskEventId = headers.getOrDefault(
      "eventId",
      "RME-" + Integer.toUnsignedString(Objects.hash(command.tenantId(), command.sourceEventId()))
    );
    Instant now = Instant.now();
    ExceptionModels.RiskMonitoringEventRecord record = new ExceptionModels.RiskMonitoringEventRecord(
      command.tenantId(),
      riskEventId,
      command.sourceEventType(),
      command.sourceEventId(),
      rule.signalType(),
      rule.severity(),
      status,
      topic,
      eventKey,
      Map.copyOf(headers),
      Map.copyOf(payload),
      payloadHash,
      Map.copyOf(redactionManifest),
      mappingVersionId,
      rule.schemaVersion(),
      "AUDIT-" + riskEventId,
      "risk_monitoring_events.completed.v1",
      replayFlag,
      command.idempotencyKey(),
      requestHash,
      command.correlationId(),
      now
    );
    riskMonitoringEventStore.put(riskEventId, record);
    riskMonitoringEventIdempotencyIndex.put(riskMonitoringIdempotencyIndexKey(command.tenantId(), command.idempotencyKey()), riskEventId);
    riskMonitoringEventSourceIndex.put(riskMonitoringSourceIndexKey(command.tenantId(), command.sourceEventId()), riskEventId);
    riskMonitoringEventSequence.incrementAndGet();
    return record;
  }

  public ExceptionModels.MonitoringSignalRecord recordMonitoringSignal(
    ExceptionModels.ConcessionMonitoringSignalCommand command,
    ExceptionModels.MonitoringPolicyVersion policy,
    String signalHash
  ) {
    String signalId = "CMS-" + monitoringSignalSequence.incrementAndGet();
    Instant now = Instant.now();
    ExceptionModels.MonitoringSignalRecord record = new ExceptionModels.MonitoringSignalRecord(
      command.tenantId(),
      signalId,
      command.sourceEventId(),
      command.concessionRequestId(),
      command.applicationId(),
      command.approvalDecisionId(),
      command.signalType(),
      policy.detectorId(),
      policy.detectorVersionId(),
      Map.copyOf(command.dimensions()),
      Map.copyOf(command.measurements()),
      command.actorId(),
      command.idempotencyKey(),
      command.correlationId(),
      signalHash,
      now
    );
    monitoringSignalStore.put(signalId, record);
    monitoringSignalIdempotencyIndex.put(monitoringIdempotencyIndexKey(command.tenantId(), command.idempotencyKey()), signalId);
    return record;
  }

  public ExceptionModels.MonitoringAlertRecord raiseMonitoringAlert(
    ExceptionModels.MonitoringSignalRecord signal,
    ExceptionModels.MonitoringPolicyVersion policy,
    ExceptionModels.AlertStatus status,
    Map<String, String> evidenceSnapshot,
    String fairnessCohortRef,
    boolean fairnessCohortSuppressed,
    String evidenceHash,
    String replayHash
  ) {
    String alertId = "CMA-" + monitoringAlertSequence.incrementAndGet();
    Instant now = Instant.now();
    ExceptionModels.MonitoringAlertRecord record = new ExceptionModels.MonitoringAlertRecord(
      signal.tenantId(),
      alertId,
      signal.signalId(),
      policy.detectorId(),
      policy.detectorVersionId(),
      policy.severity(),
      status,
      List.of(signal.sourceEventId()),
      Map.copyOf(evidenceSnapshot),
      fairnessCohortRef,
      fairnessCohortSuppressed,
      "AUDIT-" + alertId,
      "ConcessionMonitoringAlertRaised.v1",
      evidenceHash,
      replayHash,
      signal.correlationId(),
      1,
      now,
      now
    );
    monitoringAlertStore.put(alertId, record);
    monitoringAlertBySignalIndex.put(signal.signalId(), alertId);
    return record;
  }

  public Optional<ExceptionModels.AlertDispositionRecord> findAlertDispositionByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    String dispositionId = alertDispositionIdempotencyIndex.get(monitoringIdempotencyIndexKey(tenantId, idempotencyKey));
    if (dispositionId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(alertDispositionStore.get(dispositionId));
  }

  public ExceptionModels.AlertDispositionRecord dispositionMonitoringAlert(
    ExceptionModels.MonitoringAlertRecord alert,
    ExceptionModels.ConcessionAlertDispositionCommand command,
    ExceptionModels.AlertStatus newStatus,
    String commentRedacted,
    String dispositionHash
  ) {
    String dispositionId = "CAD-" + alertDispositionSequence.incrementAndGet();
    Instant now = Instant.now();
    int nextVersion = alert.version() + 1;
    ExceptionModels.AlertDispositionRecord disposition = new ExceptionModels.AlertDispositionRecord(
      alert.tenantId(),
      dispositionId,
      alert.alertId(),
      alert.status(),
      newStatus,
      command.reasonCode(),
      commentRedacted,
      command.actorId(),
      command.idempotencyKey(),
      command.correlationId(),
      "AUDIT-" + dispositionId,
      "ConcessionMonitoringAlertDispositioned.v1",
      dispositionHash,
      nextVersion,
      now
    );
    alertDispositionStore.put(dispositionId, disposition);
    alertDispositionIdempotencyIndex.put(monitoringIdempotencyIndexKey(command.tenantId(), command.idempotencyKey()), dispositionId);
    monitoringAlertStore.put(alert.alertId(), new ExceptionModels.MonitoringAlertRecord(
      alert.tenantId(),
      alert.alertId(),
      alert.signalId(),
      alert.detectorId(),
      alert.detectorVersionId(),
      alert.severity(),
      newStatus,
      alert.sourceEventIds(),
      alert.evidenceSnapshot(),
      alert.fairnessCohortRef(),
      alert.fairnessCohortSuppressed(),
      alert.auditRef(),
      alert.outboxEventType(),
      alert.evidenceHash(),
      alert.replayHash(),
      alert.correlationId(),
      nextVersion,
      alert.openedAt(),
      now
    ));
    return disposition;
  }

  public Optional<ExceptionModels.AuthorityMatrixVersionRecord> findAuthorityMatrixById(
    UUID tenantId,
    String matrixVersionId
  ) {
    ExceptionModels.AuthorityMatrixVersionRecord record = authorityMatrixStore.get(matrixVersionId);
    if (record == null || !record.tenantId().equals(tenantId)) {
      return Optional.empty();
    }
    return Optional.of(record);
  }

  public Optional<ExceptionModels.AuthorityMatrixVersionRecord> findAuthorityMatrixByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    String matrixVersionId = authorityMatrixIdempotencyIndex.get(authorityMatrixIdempotencyIndexKey(tenantId, idempotencyKey));
    if (matrixVersionId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(authorityMatrixStore.get(matrixVersionId));
  }

  public Optional<ExceptionModels.ManualPriceEditAttemptRecord> findManualPriceEditAttemptByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    String attemptId = manualPriceEditAttemptIdempotencyIndex.get(manualPriceEditAttemptIdempotencyIndexKey(tenantId, idempotencyKey));
    if (attemptId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(manualPriceEditAttemptStore.get(attemptId));
  }

  public Optional<ExceptionModels.ManualPriceEditAttemptRecord> findManualPriceEditAttemptById(
    UUID tenantId,
    String attemptId
  ) {
    ExceptionModels.ManualPriceEditAttemptRecord record = manualPriceEditAttemptStore.get(attemptId);
    if (record == null || !record.tenantId().equals(tenantId)) {
      return Optional.empty();
    }
    return Optional.of(record);
  }

  public ExceptionModels.ExceptionHistoryProjectionRecord saveHistoryProjection(
    ExceptionModels.ExceptionHistoryTimeline timeline
  ) {
    Instant latestEventAt = timeline.events().stream()
      .map(ExceptionModels.TimelineEvent::occurredAt)
      .max(Comparator.naturalOrder())
      .orElse(timeline.rebuiltAt());
    String projectionId = historyProjectionId(timeline.tenantId(), timeline.subjectType(), timeline.subjectId());
    ExceptionModels.ExceptionHistoryProjectionRecord record = new ExceptionModels.ExceptionHistoryProjectionRecord(
      timeline.tenantId(),
      projectionId,
      timeline.subjectType(),
      timeline.subjectId(),
      timeline,
      timeline.versionGraph(),
      latestEventAt,
      timeline.projectionHash(),
      timeline.rebuiltAt()
    );
    historyProjectionStore.put(projectionId, record);
    return record;
  }

  public Optional<ExceptionModels.ExceptionHistoryProjectionRecord> findHistoryProjection(
    UUID tenantId,
    ExceptionModels.ExceptionHistorySubjectType subjectType,
    String subjectId
  ) {
    return Optional.ofNullable(historyProjectionStore.get(historyProjectionId(tenantId, subjectType, subjectId)));
  }

  public ExceptionModels.ExceptionHistoryReplayResult saveHistoryReplay(
    ExceptionModels.ExceptionHistoryReplayResult replay
  ) {
    historyReplayStore.put(replay.replayId(), replay);
    return replay;
  }

  public Optional<ExceptionModels.ExceptionHistoryReplayResult> findHistoryReplay(
    UUID tenantId,
    String replayId
  ) {
    ExceptionModels.ExceptionHistoryReplayResult record = historyReplayStore.get(replayId);
    if (record == null || !record.tenantId().equals(tenantId)) {
      return Optional.empty();
    }
    return Optional.of(record);
  }

  public ExceptionModels.ExceptionHistoryExportPacket saveHistoryExport(
    ExceptionModels.ExceptionHistoryExportPacket export
  ) {
    historyExportStore.put(export.manifest().exportId(), export);
    return export;
  }

  public Optional<ExceptionModels.ExceptionHistoryExportPacket> findHistoryExport(
    UUID tenantId,
    String exportId
  ) {
    ExceptionModels.ExceptionHistoryExportPacket record = historyExportStore.get(exportId);
    if (record == null || !record.tenantId().equals(tenantId)) {
      return Optional.empty();
    }
    return Optional.of(record);
  }

  public ExceptionModels.ExceptionHistoryAuditRecord recordHistoryAudit(
    UUID tenantId,
    String action,
    ExceptionModels.ExceptionHistorySubjectType subjectType,
    String subjectId,
    String actorId,
    String purpose,
    String resultHash,
    String correlationId
  ) {
    String auditId = "AUDIT-HISTORY-" + Integer.toHexString(Objects.hash(tenantId, action, subjectType, subjectId, actorId, resultHash, correlationId));
    ExceptionModels.ExceptionHistoryAuditRecord record = new ExceptionModels.ExceptionHistoryAuditRecord(
      tenantId,
      auditId,
      action,
      subjectType,
      subjectId,
      actorId,
      purpose,
      resultHash,
      correlationId,
      Instant.now()
    );
    historyAuditStore.put(auditId, record);
    return record;
  }

  public ExceptionModels.ManualPriceEditAttemptRecord recordManualPriceEditAttempt(
    ExceptionModels.GuardPriceMutationCommand command,
    List<String> guardedFields,
    String payloadHash,
    String denialReason,
    String policyVersionId,
    String eventHash
  ) {
    String attemptId = "MPE-" + manualPriceEditAttemptSequence.incrementAndGet();
    Instant now = Instant.now();
    ExceptionModels.ManualPriceEditAttemptRecord record = new ExceptionModels.ManualPriceEditAttemptRecord(
      command.tenantId(),
      attemptId,
      command.actorId(),
      command.sourceSurface(),
      command.targetType(),
      command.quoteId(),
      command.lockId(),
      List.copyOf(guardedFields),
      payloadHash,
      denialReason,
      policyVersionId,
      "AUDIT-" + attemptId,
      "ManualPriceEditBlocked.v1",
      eventHash,
      command.idempotencyKey(),
      command.correlationId(),
      now
    );
    manualPriceEditAttemptStore.put(attemptId, record);
    manualPriceEditAttemptIdempotencyIndex.put(manualPriceEditAttemptIdempotencyIndexKey(command.tenantId(), command.idempotencyKey()), attemptId);
    return record;
  }

  public ExceptionModels.AuthorityMatrixVersionRecord createAuthorityMatrixDraft(
    ExceptionModels.CreateAuthorityMatrixDraftCommand command,
    List<ExceptionModels.AuthorityMatrixRuleDraft> normalizedRules,
    List<ExceptionModels.AuthorityMatrixValidationMessage> validationMessages,
    String validationHash,
    String requestHash
  ) {
    String matrixVersionId = "AMV-" + authorityMatrixSequence.incrementAndGet();
    Instant now = Instant.now();
    ExceptionModels.AuthorityMatrixVersionRecord record = new ExceptionModels.AuthorityMatrixVersionRecord(
      command.tenantId(),
      matrixVersionId,
      ExceptionModels.AuthorityMatrixVersionStatus.DRAFT,
      command.versionLabel(),
      command.sourceVersionId(),
      List.copyOf(normalizedRules),
      List.copyOf(validationMessages),
      validationHash,
      null,
      command.actorId(),
      null,
      null,
      null,
      "AUDIT-" + matrixVersionId,
      "AuthorityMatrixDraftCreated.v1",
      requestHash,
      requestHash,
      command.idempotencyKey(),
      command.correlationId(),
      1,
      now,
      now
    );
    authorityMatrixStore.put(matrixVersionId, record);
    authorityMatrixIdempotencyIndex.put(authorityMatrixIdempotencyIndexKey(command.tenantId(), command.idempotencyKey()), matrixVersionId);
    return record;
  }

  public ExceptionModels.AuthorityMatrixVersionRecord saveAuthorityMatrixVersion(
    ExceptionModels.AuthorityMatrixVersionRecord record
  ) {
    authorityMatrixStore.put(record.matrixVersionId(), record);
    return record;
  }

  public void suspendPublishedAuthorityMatrices(UUID tenantId, String exceptMatrixVersionId, Instant updatedAt) {
    authorityMatrixStore.forEach((id, record) -> {
      if (record.tenantId().equals(tenantId)
        && !record.matrixVersionId().equals(exceptMatrixVersionId)
        && record.status() == ExceptionModels.AuthorityMatrixVersionStatus.PUBLISHED) {
        authorityMatrixStore.put(id, new ExceptionModels.AuthorityMatrixVersionRecord(
          record.tenantId(), record.matrixVersionId(), ExceptionModels.AuthorityMatrixVersionStatus.SUSPENDED,
          record.versionLabel(), record.sourceVersionId(), record.rules(), record.validationMessages(),
          record.validationHash(), record.approvalTicketRef(), record.submittedBy(), record.approvedBy(),
          record.publishedBy(), record.effectiveFrom(), record.auditRef(), "AuthorityMatrixVersionSuspended.v1",
          record.eventHash(), record.requestHash(), record.idempotencyKey(), record.correlationId(),
          record.version() + 1, record.createdAt(), updatedAt
        ));
      }
    });
  }

  public Optional<ExceptionModels.AuthorityMatrixVersionRecord> findPublishedAuthorityMatrix(
    UUID tenantId,
    Instant effectiveAt
  ) {
    return authorityMatrixStore.values().stream()
      .filter(record -> record.tenantId().equals(tenantId))
      .filter(record -> record.status() == ExceptionModels.AuthorityMatrixVersionStatus.PUBLISHED)
      .filter(record -> record.effectiveFrom() != null && !Instant.parse(record.effectiveFrom()).isAfter(effectiveAt))
      .max(Comparator.comparing(record -> Instant.parse(record.effectiveFrom())));
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

  public List<ExceptionModels.ExceptionRequestRecord> exceptionRequests() {
    return List.copyOf(store.values());
  }

  public List<ExceptionModels.PricingConcessionRequestRecord> concessionRequests() {
    return List.copyOf(concessionStore.values());
  }

  public List<ExceptionModels.ApprovalDecisionRecord> approvalDecisions() {
    return List.copyOf(approvalStore.values());
  }

  public List<ExceptionModels.ConcessionApplicationRecord> concessionApplications() {
    return List.copyOf(applicationStore.values());
  }

  public List<ExceptionModels.EligibilityExceptionRequestRecord> eligibilityExceptionRequests() {
    return List.copyOf(eligibilityExceptionStore.values());
  }

  public List<ExceptionModels.MonitoringSignalRecord> monitoringSignals() {
    return List.copyOf(monitoringSignalStore.values());
  }

  public List<ExceptionModels.MonitoringAlertRecord> monitoringAlerts() {
    return List.copyOf(monitoringAlertStore.values());
  }

  public List<ExceptionModels.RiskMonitoringEventRecord> riskMonitoringEvents() {
    return List.copyOf(riskMonitoringEventStore.values());
  }

  public List<ExceptionModels.AlertDispositionRecord> alertDispositions() {
    return List.copyOf(alertDispositionStore.values());
  }

  public List<ExceptionModels.ManualPriceEditAttemptRecord> manualPriceEditAttempts() {
    return List.copyOf(manualPriceEditAttemptStore.values());
  }

  public List<ExceptionModels.ExceptionHistoryProjectionRecord> historyProjections() {
    return List.copyOf(historyProjectionStore.values());
  }

  public List<ExceptionModels.ExceptionHistoryReplayResult> historyReplays() {
    return List.copyOf(historyReplayStore.values());
  }

  public List<ExceptionModels.ExceptionHistoryExportPacket> historyExports() {
    return List.copyOf(historyExportStore.values());
  }

  public List<ExceptionModels.ExceptionHistoryAuditRecord> historyAudits() {
    return List.copyOf(historyAuditStore.values());
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
    monitoringSignalStore.clear();
    monitoringSignalIdempotencyIndex.clear();
    monitoringAlertStore.clear();
    monitoringAlertBySignalIndex.clear();
    riskMonitoringEventStore.clear();
    riskMonitoringEventIdempotencyIndex.clear();
    riskMonitoringEventSourceIndex.clear();
    alertDispositionStore.clear();
    alertDispositionIdempotencyIndex.clear();
    eligibilityExceptionStore.clear();
    eligibilityExceptionIdempotencyIndex.clear();
    activeEligibilityExceptionIndex.clear();
    authorityMatrixStore.clear();
    authorityMatrixIdempotencyIndex.clear();
    manualPriceEditAttemptStore.clear();
    manualPriceEditAttemptIdempotencyIndex.clear();
    historyProjectionStore.clear();
    historyReplayStore.clear();
    historyExportStore.clear();
    historyAuditStore.clear();
    sequence.set(0);
    concessionSequence.set(0);
    approvalSequence.set(0);
    applicationSequence.set(0);
    monitoringSignalSequence.set(0);
    monitoringAlertSequence.set(0);
    riskMonitoringEventSequence.set(0);
    alertDispositionSequence.set(0);
    eligibilityExceptionSequence.set(0);
    authorityMatrixSequence.set(0);
    manualPriceEditAttemptSequence.set(0);
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

  private static String monitoringIdempotencyIndexKey(UUID tenantId, String idempotencyKey) {
    return tenantId + ":" + idempotencyKey;
  }

  private static String riskMonitoringIdempotencyIndexKey(UUID tenantId, String idempotencyKey) {
    return tenantId + ":" + idempotencyKey;
  }

  private static String riskMonitoringSourceIndexKey(UUID tenantId, String sourceEventId) {
    return tenantId + ":" + sourceEventId;
  }

  private static String eligibilityExceptionIdempotencyIndexKey(UUID tenantId, String idempotencyKey) {
    return tenantId + ":" + idempotencyKey;
  }

  private static String authorityMatrixIdempotencyIndexKey(UUID tenantId, String idempotencyKey) {
    return tenantId + ":" + idempotencyKey;
  }

  private static String manualPriceEditAttemptIdempotencyIndexKey(UUID tenantId, String idempotencyKey) {
    return tenantId + ":" + idempotencyKey;
  }

  private static String historyProjectionId(
    UUID tenantId,
    ExceptionModels.ExceptionHistorySubjectType subjectType,
    String subjectId
  ) {
    return tenantId + ":" + subjectType.name() + ":" + subjectId;
  }

  private static String activeEligibilityExceptionIndexKey(
    UUID tenantId,
    ExceptionModels.EligibilityFindingRef findingRef,
    ExceptionModels.EligibilityExceptionScope exceptionScope
  ) {
    return tenantId + ":" + findingRef.eligibilityResultId() + ":" + findingRef.findingId() + ":"
      + findingRef.ruleCode() + ":" + findingRef.ruleVersionId() + ":" + exceptionScope.scopeType() + ":"
      + new TreeMap<>(exceptionScope.attributes()).toString();
  }
}
