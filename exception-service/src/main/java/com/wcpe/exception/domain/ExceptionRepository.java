package com.wcpe.exception.domain;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable repository boundary for exception lifecycle state.
 *
 * <p>The service module is not wired to an external database in local/dev mode, but it does carry
 * migration-backed record contracts for exception, concession, approval, application, monitoring,
 * authority-matrix, manual-edit, and history surfaces. This repository keeps those contracts as the
 * source of truth and persists them to a configured JSON store so lifecycle/history/replay/export/workbench
 * flows do not silently fall back to request-local or service-local maps. The no-arg constructor fails
 * closed outside local/dev/test unless a durable store path is supplied by configuration.</p>
 */
public class ExceptionRepository {

  static final String REPOSITORY_PATH_PROPERTY = "wcpe.exception.repository.path";
  static final String LOCAL_JSON_STORE_PROPERTY = "wcpe.exception.local-json-store.enabled";
  static final String REPOSITORY_PATH_ENV = "WCPE_EXCEPTION_REPOSITORY_PATH";

  private static final ObjectMapper JSON = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private final Object lock = new Object();
  private final Path storageFile;
  private Store store;

  public ExceptionRepository() {
    this(resolveDefaultStorageFile());
  }

  public ExceptionRepository(Path storageFile) {
    this.storageFile = Objects.requireNonNull(storageFile, "storageFile");
    this.store = load(storageFile);
  }

  public ExceptionModels.ExceptionRequestRecord create(ExceptionModels.ExceptionRequestCreate request) {
    synchronized (lock) {
      Instant now = Instant.now();
      ExceptionModels.ExceptionRequestRecord record = new ExceptionModels.ExceptionRequestRecord(
        nextId("EXC"), request.placeholderQuoteReference().trim(), request.requestType(), ExceptionModels.ExceptionState.DRAFT, now, now
      );
      store.exceptionRequests.put(record.exceptionRequestId(), record);
      persist();
      return record;
    }
  }

  public Optional<ExceptionModels.ExceptionRequestRecord> findById(String id) {
    synchronized (lock) {
      return Optional.ofNullable(store.exceptionRequests.get(id));
    }
  }

  public Optional<ExceptionModels.PricingConcessionRequestRecord> findConcessionById(String tenantId, String id) {
    synchronized (lock) {
      return Optional.ofNullable(store.concessionRequests.get(id))
        .filter(record -> record.tenantId().toString().equals(tenantId));
    }
  }

  public Optional<ExceptionModels.PricingConcessionRequestRecord> findConcessionByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    synchronized (lock) {
      return store.concessionRequests.values().stream()
        .filter(record -> record.tenantId().equals(tenantId))
        .filter(record -> record.idempotencyKey().equals(idempotencyKey))
        .findFirst();
    }
  }

  public Optional<ExceptionModels.EligibilityExceptionRequestRecord> findEligibilityExceptionById(
    UUID tenantId,
    String id
  ) {
    synchronized (lock) {
      return Optional.ofNullable(store.eligibilityExceptionRequests.get(id))
        .filter(record -> record.tenantId().equals(tenantId));
    }
  }

  public Optional<ExceptionModels.EligibilityExceptionRequestRecord> findEligibilityExceptionByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    synchronized (lock) {
      return store.eligibilityExceptionRequests.values().stream()
        .filter(record -> record.tenantId().equals(tenantId))
        .filter(record -> record.idempotencyKey().equals(idempotencyKey))
        .findFirst();
    }
  }

  public Optional<ExceptionModels.EligibilityExceptionRequestRecord> findActiveEligibilityException(
    UUID tenantId,
    ExceptionModels.EligibilityFindingRef findingRef,
    ExceptionModels.EligibilityExceptionScope exceptionScope
  ) {
    synchronized (lock) {
      return store.eligibilityExceptionRequests.values().stream()
        .filter(record -> record.tenantId().equals(tenantId))
        .filter(record -> record.status() == ExceptionModels.EligibilityExceptionRequestStatus.DRAFT
          || record.status() == ExceptionModels.EligibilityExceptionRequestStatus.SUBMITTED)
        .filter(record -> Objects.equals(record.findingRef(), findingRef))
        .filter(record -> Objects.equals(record.exceptionScope(), exceptionScope))
        .findFirst();
    }
  }

  public ExceptionModels.EligibilityExceptionRequestRecord createEligibilityException(
    ExceptionModels.CreateEligibilityExceptionRequest request,
    ExceptionModels.EligibilityExceptionPolicy policy,
    String narrativeRedacted,
    String approvalRouteHash,
    String requestHash,
    String eventHash
  ) {
    synchronized (lock) {
      Instant now = Instant.now();
      ExceptionModels.EligibilityExceptionRequestRecord record = new ExceptionModels.EligibilityExceptionRequestRecord(
        request.tenantId(), nextId("ELX"), request.quoteId(), request.scenarioId(), request.lockId(), request.findingRef(),
        request.exceptionScope(), request.reasonCode(), narrativeRedacted, safeList(request.compensatingFactors()),
        safeList(request.evidenceRefs()), request.desiredExpiration(), request.relatedConcessionRequestId(),
        ExceptionModels.EligibilityExceptionRequestStatus.SUBMITTED, policy.policyVersionId(), policy.authorityMatrixVersionId(),
        approvalRouteHash, request.findingRef().originalResultHash(), auditRef("ELIGIBILITY", request.correlationId()),
        "EligibilityExceptionRequested.v1", eventHash, requestHash, request.idempotencyKey(), request.actorId(), request.correlationId(),
        1, now, now
      );
      store.eligibilityExceptionRequests.put(record.exceptionRequestId(), record);
      persist();
      return record;
    }
  }

  public ExceptionModels.PricingConcessionRequestRecord createConcession(
    ExceptionModels.PricingConcessionRequestCreate request,
    ExceptionModels.ConcessionRequestStatus status,
    String commentsRedacted,
    String approvalRouteHash,
    String requestHash
  ) {
    synchronized (lock) {
      Instant now = Instant.now();
      ExceptionModels.PricingConcessionRequestRecord record = new ExceptionModels.PricingConcessionRequestRecord(
        request.tenantId(), nextId("PCR"), request.quoteId(), request.scenarioId(), request.lockId(), status,
        request.requestedAmount(), request.reasonCode(), commentsRedacted, safeList(request.evidenceRefs()), request.expiration(),
        request.concessionPolicyVersionId(), request.approvalRouteSnapshot().authorityMatrixVersionId(),
        request.reasonCodeVersionId(), request.quoteSnapshotHash(), approvalRouteHash,
        safeList(request.approvalRouteSnapshot().approverGroups()), request.approvalRouteSnapshot().sla(), request.idempotencyKey(),
        request.actorId(), request.correlationId(), auditRef("CONCESSION", request.correlationId()), "ConcessionRequested.v1",
        requestHash, 1, now, now
      );
      store.concessionRequests.put(record.concessionRequestId(), record);
      persist();
      return record;
    }
  }

  public Optional<ExceptionModels.ApprovalDecisionRecord> findApprovalByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    synchronized (lock) {
      return store.approvalDecisions.values().stream()
        .filter(record -> record.tenantId().equals(tenantId))
        .filter(record -> record.idempotencyKey().equals(idempotencyKey))
        .findFirst();
    }
  }

  public ExceptionModels.ApprovalDecisionRecord approveConcession(
    ExceptionModels.PricingConcessionRequestRecord request,
    ExceptionModels.ApproveConcessionRequest approval,
    String commentRedacted,
    String eventHash
  ) {
    synchronized (lock) {
      Instant now = Instant.now();
      int nextVersion = request.version() + 1;
      ExceptionModels.ApprovalDecisionRecord decision = new ExceptionModels.ApprovalDecisionRecord(
        request.tenantId(), nextId("DEC"), request.concessionRequestId(), approval.routeStepId(), approval.decision(),
        approval.reasonCode(), commentRedacted, approval.conditions(), approval.authorityMatrixVersionId(), approval.actorId(),
        safeList(approval.actorRoleRefs()), approval.conflictAttestation(), approval.idempotencyKey(), approval.correlationId(),
        auditRef("APPROVAL", approval.correlationId()), "ConcessionApproved.v1", eventHash, nextVersion, now
      );
      store.approvalDecisions.put(decision.decisionId(), decision);
      store.concessionRequests.put(request.concessionRequestId(), new ExceptionModels.PricingConcessionRequestRecord(
        request.tenantId(), request.concessionRequestId(), request.quoteId(), request.scenarioId(), request.lockId(),
        ExceptionModels.ConcessionRequestStatus.APPROVED_PENDING_APPLICATION, request.requestedAmount(), request.reasonCode(),
        request.commentsRedacted(), request.evidenceRefs(), request.expiration(), request.concessionPolicyVersionId(),
        request.authorityMatrixVersionId(), request.reasonCodeVersionId(), request.quoteSnapshotHash(), request.approvalRouteHash(),
        request.nextApproverGroups(), request.sla(), request.idempotencyKey(), request.actorId(), request.correlationId(),
        request.auditRef(), request.outboxEventType(), request.requestHash(), nextVersion, request.createdAt(), now
      ));
      persist();
      return decision;
    }
  }

  public Optional<ExceptionModels.ConcessionApplicationRecord> findApplicationByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    synchronized (lock) {
      return store.concessionApplications.values().stream()
        .filter(record -> record.tenantId().equals(tenantId))
        .filter(record -> record.idempotencyKey().equals(idempotencyKey))
        .findFirst();
    }
  }

  public Optional<ExceptionModels.ConcessionApplicationRecord> findApplicationByTarget(
    UUID tenantId,
    String concessionRequestId,
    ExceptionModels.ApplicationTarget target
  ) {
    synchronized (lock) {
      return store.concessionApplications.values().stream()
        .filter(record -> record.tenantId().equals(tenantId))
        .filter(record -> record.concessionRequestId().equals(concessionRequestId))
        .filter(record -> record.targetType() == target.targetType())
        .filter(record -> Objects.equals(record.quoteId(), target.quoteId()))
        .filter(record -> Objects.equals(record.lockId(), target.lockId()))
        .findFirst();
    }
  }

  public ExceptionModels.ConcessionApplicationRecord applyConcession(
    ExceptionModels.PricingConcessionRequestRecord request,
    ExceptionModels.ApplyApprovedConcessionRequest command,
    String pricingLedgerEntryId,
    String afterPriceHash,
    String replayHash,
    String outboxEventType
  ) {
    synchronized (lock) {
      Instant now = Instant.now();
      ExceptionModels.ConcessionApplicationRecord application = new ExceptionModels.ConcessionApplicationRecord(
        request.tenantId(), nextId("APP"), request.concessionRequestId(), command.target().targetType(), command.target().quoteId(),
        command.target().lockId(), request.requestedAmount(), pricingLedgerEntryId, command.expectedLedgerHash(), afterPriceHash,
        command.pricingRuleVersionId(), command.policyVersionId(), command.precedence().precedenceConfigVersionId(),
        command.precedence().scale(), command.precedence().roundingMode(), ExceptionModels.ConcessionRequestStatus.APPLIED,
        command.idempotencyKey(), command.actorId(), command.correlationId(), auditRef("APPLICATION", command.correlationId()),
        outboxEventType, replayHash, 1, now
      );
      store.concessionApplications.put(application.applicationId(), application);
      store.concessionRequests.put(request.concessionRequestId(), new ExceptionModels.PricingConcessionRequestRecord(
        request.tenantId(), request.concessionRequestId(), request.quoteId(), request.scenarioId(), request.lockId(),
        ExceptionModels.ConcessionRequestStatus.APPLIED, request.requestedAmount(), request.reasonCode(), request.commentsRedacted(),
        request.evidenceRefs(), request.expiration(), request.concessionPolicyVersionId(), request.authorityMatrixVersionId(),
        request.reasonCodeVersionId(), request.quoteSnapshotHash(), request.approvalRouteHash(), request.nextApproverGroups(),
        request.sla(), request.idempotencyKey(), request.actorId(), request.correlationId(), request.auditRef(),
        request.outboxEventType(), request.requestHash(), request.version() + 1, request.createdAt(), now
      ));
      persist();
      return application;
    }
  }

  public Optional<ExceptionModels.MonitoringSignalRecord> findMonitoringSignalByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    synchronized (lock) {
      return store.monitoringSignals.values().stream()
        .filter(record -> record.tenantId().equals(tenantId))
        .filter(record -> record.idempotencyKey().equals(idempotencyKey))
        .findFirst();
    }
  }

  public Optional<ExceptionModels.MonitoringAlertRecord> findMonitoringAlertBySignalId(String signalId) {
    synchronized (lock) {
      return store.monitoringAlerts.values().stream().filter(record -> record.signalId().equals(signalId)).findFirst();
    }
  }

  public Optional<ExceptionModels.MonitoringAlertRecord> findMonitoringAlertById(UUID tenantId, String alertId) {
    synchronized (lock) {
      return Optional.ofNullable(store.monitoringAlerts.get(alertId)).filter(record -> record.tenantId().equals(tenantId));
    }
  }

  public Optional<ExceptionModels.RiskMonitoringEventRecord> findRiskMonitoringEventByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    synchronized (lock) {
      return store.riskMonitoringEvents.values().stream()
        .filter(record -> record.tenantId().equals(tenantId))
        .filter(record -> record.idempotencyKey().equals(idempotencyKey))
        .findFirst();
    }
  }

  public Optional<ExceptionModels.RiskMonitoringEventRecord> findRiskMonitoringEventBySourceEvent(
    UUID tenantId,
    String sourceEventId
  ) {
    synchronized (lock) {
      return store.riskMonitoringEvents.values().stream()
        .filter(record -> record.tenantId().equals(tenantId))
        .filter(record -> record.sourceEventId().equals(sourceEventId))
        .findFirst();
    }
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
    synchronized (lock) {
      Instant now = Instant.now();
      String riskEventId = "RME-" + stableUuid(command.tenantId() + "|" + command.sourceEventId()).substring(0, 16);
      ExceptionModels.RiskMonitoringEventRecord record = new ExceptionModels.RiskMonitoringEventRecord(
        command.tenantId(), riskEventId, command.sourceEventType(), command.sourceEventId(), rule.signalType(), rule.severity(),
        status, topic, eventKey, safeMap(headers), safeMap(payload), payloadHash, safeMap(redactionManifest), mappingVersionId,
        rule.schemaVersion(), auditRef("RISK", command.correlationId()), "RiskMonitoringEventMapped.v1", replayFlag,
        command.idempotencyKey(), requestHash, command.correlationId(), now
      );
      store.riskMonitoringEvents.put(record.riskEventId(), record);
      persist();
      return record;
    }
  }

  public ExceptionModels.MonitoringSignalRecord recordMonitoringSignal(
    ExceptionModels.ConcessionMonitoringSignalCommand command,
    ExceptionModels.MonitoringPolicyVersion policy,
    String signalHash
  ) {
    synchronized (lock) {
      ExceptionModels.MonitoringSignalRecord record = new ExceptionModels.MonitoringSignalRecord(
        command.tenantId(), nextId("SIG"), command.sourceEventId(), command.concessionRequestId(), command.applicationId(),
        command.approvalDecisionId(), command.signalType(), policy.detectorId(), policy.detectorVersionId(),
        safeMap(command.dimensions()), safeMap(command.measurements()), command.actorId(), command.idempotencyKey(),
        command.correlationId(), signalHash, Instant.now()
      );
      store.monitoringSignals.put(record.signalId(), record);
      persist();
      return record;
    }
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
    synchronized (lock) {
      Instant now = Instant.now();
      ExceptionModels.MonitoringAlertRecord record = new ExceptionModels.MonitoringAlertRecord(
        signal.tenantId(), nextId("ALT"), signal.signalId(), policy.detectorId(), policy.detectorVersionId(), policy.severity(),
        status, List.of(signal.sourceEventId()), safeMap(evidenceSnapshot), fairnessCohortRef, fairnessCohortSuppressed,
        auditRef("ALERT", signal.correlationId()), "ConcessionMonitoringAlertRaised.v1", evidenceHash, replayHash,
        signal.correlationId(), 1, now, now
      );
      store.monitoringAlerts.put(record.alertId(), record);
      persist();
      return record;
    }
  }

  public Optional<ExceptionModels.AlertDispositionRecord> findAlertDispositionByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    synchronized (lock) {
      return store.alertDispositions.values().stream()
        .filter(record -> record.tenantId().equals(tenantId))
        .filter(record -> record.idempotencyKey().equals(idempotencyKey))
        .findFirst();
    }
  }

  public ExceptionModels.AlertDispositionRecord dispositionMonitoringAlert(
    ExceptionModels.MonitoringAlertRecord alert,
    ExceptionModels.ConcessionAlertDispositionCommand command,
    ExceptionModels.AlertStatus newStatus,
    String commentRedacted,
    String dispositionHash
  ) {
    synchronized (lock) {
      Instant now = Instant.now();
      ExceptionModels.AlertDispositionRecord disposition = new ExceptionModels.AlertDispositionRecord(
        alert.tenantId(), nextId("DSP"), alert.alertId(), alert.status(), newStatus, command.reasonCode(), commentRedacted,
        command.actorId(), command.idempotencyKey(), command.correlationId(), auditRef("DISPOSITION", command.correlationId()),
        "ConcessionMonitoringAlertDispositioned.v1", dispositionHash, alert.version() + 1, now
      );
      store.alertDispositions.put(disposition.dispositionId(), disposition);
      store.monitoringAlerts.put(alert.alertId(), new ExceptionModels.MonitoringAlertRecord(
        alert.tenantId(), alert.alertId(), alert.signalId(), alert.detectorId(), alert.detectorVersionId(), alert.severity(),
        newStatus, alert.sourceEventIds(), alert.evidenceSnapshot(), alert.fairnessCohortRef(), alert.fairnessCohortSuppressed(),
        alert.auditRef(), alert.outboxEventType(), alert.evidenceHash(), alert.replayHash(), alert.correlationId(),
        alert.version() + 1, alert.openedAt(), now
      ));
      persist();
      return disposition;
    }
  }

  public Optional<ExceptionModels.AuthorityMatrixVersionRecord> findAuthorityMatrixById(
    UUID tenantId,
    String matrixVersionId
  ) {
    synchronized (lock) {
      return Optional.ofNullable(store.authorityMatrixVersions.get(matrixVersionId)).filter(record -> record.tenantId().equals(tenantId));
    }
  }

  public Optional<ExceptionModels.AuthorityMatrixVersionRecord> findAuthorityMatrixByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    synchronized (lock) {
      return store.authorityMatrixVersions.values().stream()
        .filter(record -> record.tenantId().equals(tenantId))
        .filter(record -> record.idempotencyKey().equals(idempotencyKey))
        .findFirst();
    }
  }

  public Optional<ExceptionModels.ManualPriceEditAttemptRecord> findManualPriceEditAttemptByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    synchronized (lock) {
      return store.manualPriceEditAttempts.values().stream()
        .filter(record -> record.tenantId().equals(tenantId))
        .filter(record -> record.idempotencyKey().equals(idempotencyKey))
        .findFirst();
    }
  }

  public Optional<ExceptionModels.ManualPriceEditAttemptRecord> findManualPriceEditAttemptById(
    UUID tenantId,
    String attemptId
  ) {
    synchronized (lock) {
      return Optional.ofNullable(store.manualPriceEditAttempts.get(attemptId)).filter(record -> record.tenantId().equals(tenantId));
    }
  }

  public ExceptionModels.ExceptionHistoryProjectionRecord saveHistoryProjection(
    ExceptionModels.ExceptionHistoryTimeline timeline
  ) {
    synchronized (lock) {
      Instant latest = timeline.events().stream()
        .map(ExceptionModels.TimelineEvent::occurredAt)
        .max(Comparator.naturalOrder())
        .orElse(timeline.rebuiltAt());
      String projectionId = "HIST-" + stableUuid(timeline.tenantId() + "|" + timeline.subjectType() + "|" + timeline.subjectId()).substring(0, 16);
      ExceptionModels.ExceptionHistoryProjectionRecord record = new ExceptionModels.ExceptionHistoryProjectionRecord(
        timeline.tenantId(), projectionId, timeline.subjectType(), timeline.subjectId(), timeline, timeline.versionGraph(),
        latest, timeline.projectionHash(), timeline.rebuiltAt()
      );
      store.historyProjections.put(projectionId, record);
      persist();
      return record;
    }
  }

  public Optional<ExceptionModels.ExceptionHistoryProjectionRecord> findHistoryProjection(
    UUID tenantId,
    ExceptionModels.ExceptionHistorySubjectType subjectType,
    String subjectId
  ) {
    synchronized (lock) {
      return store.historyProjections.values().stream()
        .filter(record -> record.tenantId().equals(tenantId))
        .filter(record -> record.subjectType() == subjectType)
        .filter(record -> record.subjectId().equals(subjectId))
        .findFirst();
    }
  }

  public ExceptionModels.ExceptionHistoryReplayResult saveHistoryReplay(
    ExceptionModels.ExceptionHistoryReplayResult replay) {
    synchronized (lock) {
      store.historyReplays.put(replay.replayId(), replay);
      persist();
      return replay;
    }
  }

  public Optional<ExceptionModels.ExceptionHistoryReplayResult> findHistoryReplay(UUID tenantId, String replayId) {
    synchronized (lock) {
      return Optional.ofNullable(store.historyReplays.get(replayId)).filter(record -> record.tenantId().equals(tenantId));
    }
  }

  public ExceptionModels.ExceptionHistoryExportPacket saveHistoryExport(
    ExceptionModels.ExceptionHistoryExportPacket export
  ) {
    synchronized (lock) {
      store.historyExports.put(export.manifest().exportId(), export);
      persist();
      return export;
    }
  }

  public Optional<ExceptionModels.ExceptionHistoryExportPacket> findHistoryExport(UUID tenantId, String exportId) {
    synchronized (lock) {
      return Optional.ofNullable(store.historyExports.get(exportId)).filter(record -> record.tenantId().equals(tenantId));
    }
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
    synchronized (lock) {
      ExceptionModels.ExceptionHistoryAuditRecord record = new ExceptionModels.ExceptionHistoryAuditRecord(
        tenantId, nextId("AUD"), action, subjectType, subjectId, actorId, purpose, resultHash, correlationId, Instant.now()
      );
      store.historyAudits.put(record.auditId(), record);
      persist();
      return record;
    }
  }

  public ExceptionModels.ManualPriceEditAttemptRecord recordManualPriceEditAttempt(
    ExceptionModels.GuardPriceMutationCommand command,
    List<String> guardedFields,
    String payloadHash,
    String denialReason,
    String policyVersionId,
    String eventHash
  ) {
    synchronized (lock) {
      ExceptionModels.ManualPriceEditAttemptRecord record = new ExceptionModels.ManualPriceEditAttemptRecord(
        command.tenantId(), nextId("MPE"), command.actorId(), command.sourceSurface(), command.targetType(), command.quoteId(),
        command.lockId(), safeList(guardedFields), payloadHash, denialReason, policyVersionId, auditRef("MPE", command.correlationId()),
        "ManualPriceEditBlocked.v1", eventHash, command.idempotencyKey(), command.correlationId(), Instant.now()
      );
      store.manualPriceEditAttempts.put(record.attemptId(), record);
      persist();
      return record;
    }
  }

  public ExceptionModels.AuthorityMatrixVersionRecord createAuthorityMatrixDraft(
    ExceptionModels.CreateAuthorityMatrixDraftCommand command,
    List<ExceptionModels.AuthorityMatrixRuleDraft> normalizedRules,
    List<ExceptionModels.AuthorityMatrixValidationMessage> validationMessages,
    String validationHash,
    String requestHash
  ) {
    synchronized (lock) {
      Instant now = Instant.now();
      ExceptionModels.AuthorityMatrixVersionRecord record = new ExceptionModels.AuthorityMatrixVersionRecord(
        command.tenantId(), nextId("AMV"), ExceptionModels.AuthorityMatrixVersionStatus.DRAFT, command.versionLabel(),
        command.sourceVersionId(), safeList(normalizedRules), safeList(validationMessages), validationHash, null, command.actorId(),
        null, null, null, auditRef("AUTHORITY", command.correlationId()), "AuthorityMatrixDraftCreated.v1", validationHash,
        requestHash, command.idempotencyKey(), command.correlationId(), 1, now, now
      );
      store.authorityMatrixVersions.put(record.matrixVersionId(), record);
      persist();
      return record;
    }
  }

  public ExceptionModels.AuthorityMatrixVersionRecord saveAuthorityMatrixVersion(
    ExceptionModels.AuthorityMatrixVersionRecord record
  ) {
    synchronized (lock) {
      store.authorityMatrixVersions.put(record.matrixVersionId(), record);
      persist();
      return record;
    }
  }

  public void suspendPublishedAuthorityMatrices(UUID tenantId, String exceptMatrixVersionId, Instant updatedAt) {
    synchronized (lock) {
      List<ExceptionModels.AuthorityMatrixVersionRecord> updates = store.authorityMatrixVersions.values().stream()
        .filter(record -> record.tenantId().equals(tenantId))
        .filter(record -> record.status() == ExceptionModels.AuthorityMatrixVersionStatus.PUBLISHED)
        .filter(record -> !record.matrixVersionId().equals(exceptMatrixVersionId))
        .map(record -> new ExceptionModels.AuthorityMatrixVersionRecord(
          record.tenantId(), record.matrixVersionId(), ExceptionModels.AuthorityMatrixVersionStatus.SUSPENDED,
          record.versionLabel(), record.sourceVersionId(), record.rules(), record.validationMessages(), record.validationHash(),
          record.approvalTicketRef(), record.submittedBy(), record.approvedBy(), record.publishedBy(), record.effectiveFrom(),
          record.auditRef(), "AuthorityMatrixVersionSuspended.v1", record.eventHash(), record.requestHash(),
          record.idempotencyKey(), record.correlationId(), record.version() + 1, record.createdAt(), updatedAt
        ))
        .toList();
      updates.forEach(record -> store.authorityMatrixVersions.put(record.matrixVersionId(), record));
      persist();
    }
  }

  public Optional<ExceptionModels.AuthorityMatrixVersionRecord> findPublishedAuthorityMatrix(
    UUID tenantId,
    Instant effectiveAt
  ) {
    synchronized (lock) {
      return store.authorityMatrixVersions.values().stream()
        .filter(record -> record.tenantId().equals(tenantId))
        .filter(record -> record.status() == ExceptionModels.AuthorityMatrixVersionStatus.PUBLISHED)
        .filter(record -> record.effectiveFrom() != null && Instant.parse(record.effectiveFrom()).compareTo(effectiveAt) <= 0)
        .max(Comparator.comparing(record -> Instant.parse(record.effectiveFrom())));
    }
  }

  public Optional<ExceptionModels.ExceptionRequestRecord> transition(String id, ExceptionModels.ExceptionState target) {
    synchronized (lock) {
      ExceptionModels.ExceptionRequestRecord existing = store.exceptionRequests.get(id);
      if (existing == null) {
        return Optional.empty();
      }
      if (!ExceptionModels.ExceptionState.allowedTransitions(existing.state()).contains(target)) {
        throw new ExceptionServiceException("INVALID_STATE_TRANSITION", "transition is not allowed from current exception state");
      }
      ExceptionModels.ExceptionRequestRecord updated = new ExceptionModels.ExceptionRequestRecord(
        existing.exceptionRequestId(), existing.placeholderQuoteReference(), existing.requestType(), target, existing.createdAt(), Instant.now()
      );
      store.exceptionRequests.put(id, updated);
      persist();
      return Optional.of(updated);
    }
  }

  public List<ExceptionModels.ExceptionRequestRecord> exceptionRequests() {
    synchronized (lock) { return List.copyOf(store.exceptionRequests.values()); }
  }

  public List<ExceptionModels.PricingConcessionRequestRecord> concessionRequests() {
    synchronized (lock) { return List.copyOf(store.concessionRequests.values()); }
  }

  public List<ExceptionModels.ApprovalDecisionRecord> approvalDecisions() {
    synchronized (lock) { return List.copyOf(store.approvalDecisions.values()); }
  }

  public List<ExceptionModels.ConcessionApplicationRecord> concessionApplications() {
    synchronized (lock) { return List.copyOf(store.concessionApplications.values()); }
  }

  public List<ExceptionModels.EligibilityExceptionRequestRecord> eligibilityExceptionRequests() {
    synchronized (lock) { return List.copyOf(store.eligibilityExceptionRequests.values()); }
  }

  public List<ExceptionModels.MonitoringSignalRecord> monitoringSignals() {
    synchronized (lock) { return List.copyOf(store.monitoringSignals.values()); }
  }

  public List<ExceptionModels.MonitoringAlertRecord> monitoringAlerts() {
    synchronized (lock) { return List.copyOf(store.monitoringAlerts.values()); }
  }

  public List<ExceptionModels.RiskMonitoringEventRecord> riskMonitoringEvents() {
    synchronized (lock) { return List.copyOf(store.riskMonitoringEvents.values()); }
  }

  public List<ExceptionModels.AlertDispositionRecord> alertDispositions() {
    synchronized (lock) { return List.copyOf(store.alertDispositions.values()); }
  }

  public List<ExceptionModels.ManualPriceEditAttemptRecord> manualPriceEditAttempts() {
    synchronized (lock) { return List.copyOf(store.manualPriceEditAttempts.values()); }
  }

  public List<ExceptionModels.ExceptionHistoryProjectionRecord> historyProjections() {
    synchronized (lock) { return List.copyOf(store.historyProjections.values()); }
  }

  public List<ExceptionModels.ExceptionHistoryReplayResult> historyReplays() {
    synchronized (lock) { return List.copyOf(store.historyReplays.values()); }
  }

  public List<ExceptionModels.ExceptionHistoryExportPacket> historyExports() {
    synchronized (lock) { return List.copyOf(store.historyExports.values()); }
  }

  public List<ExceptionModels.ExceptionHistoryAuditRecord> historyAudits() {
    synchronized (lock) { return List.copyOf(store.historyAudits.values()); }
  }

  void clear() {
    synchronized (lock) {
      store = new Store();
      persist();
    }
  }

  private static Store load(Path storageFile) {
    if (!Files.exists(storageFile)) {
      return new Store();
    }
    try {
      Store loaded = JSON.readValue(storageFile.toFile(), Store.class);
      return loaded == null ? new Store() : loaded.reindexed();
    } catch (IOException e) {
      throw new ExceptionServiceException("PERSISTENCE_STORE_UNREADABLE", "exception repository store cannot be read: " + e.getMessage());
    }
  }

  private static Path resolveDefaultStorageFile() {
    Optional<String> configuredPath = firstText(System.getProperty(REPOSITORY_PATH_PROPERTY), System.getenv(REPOSITORY_PATH_ENV));
    if (configuredPath.isPresent()) {
      return Paths.get(configuredPath.get());
    }
    if (localJsonStoreAllowed()) {
      return Paths.get(System.getProperty("java.io.tmpdir"), "wcpe-exception-service", "exception-repository.json");
    }
    throw new ExceptionServiceException(
      "PERSISTENCE_STORE_REQUIRED",
      "exception repository default construction requires " + REPOSITORY_PATH_PROPERTY + " or " + REPOSITORY_PATH_ENV
        + "; the local JSON temp-store adapter is enabled only for local/dev/test profiles or explicit "
        + LOCAL_JSON_STORE_PROPERTY + "=true"
    );
  }

  private static Optional<String> firstText(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return Optional.of(value.trim());
      }
    }
    return Optional.empty();
  }

  private static boolean localJsonStoreAllowed() {
    if (Boolean.getBoolean(LOCAL_JSON_STORE_PROPERTY)) {
      return true;
    }
    return profileAllowsLocalStore(System.getProperty("spring.profiles.active"))
      || profileAllowsLocalStore(System.getenv("SPRING_PROFILES_ACTIVE"))
      || profileAllowsLocalStore(System.getenv("APP_ENV"));
  }

  private static boolean profileAllowsLocalStore(String profileCsv) {
    if (profileCsv == null || profileCsv.isBlank()) {
      return false;
    }
    for (String profile : profileCsv.split(",")) {
      String normalized = profile.trim().toLowerCase(java.util.Locale.ROOT);
      if (normalized.equals("local") || normalized.equals("dev") || normalized.equals("test")) {
        return true;
      }
    }
    return false;
  }

  private void persist() {
    try {
      Path parent = storageFile.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Path lockFile = storageFile.resolveSibling(storageFile.getFileName() + ".lock");
      Path tmp = storageFile.resolveSibling(storageFile.getFileName() + ".tmp");
      try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
           FileLock ignored = channel.lock()) {
        JSON.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), store.snapshot());
        Files.move(tmp, storageFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      }
    } catch (IOException e) {
      throw new ExceptionServiceException("PERSISTENCE_STORE_UNAVAILABLE", "exception repository store cannot be written: " + e.getMessage());
    }
  }

  private static String nextId(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
  }

  private static String stableUuid(String input) {
    return UUID.nameUUIDFromBytes(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-", "").toUpperCase();
  }

  private static String auditRef(String prefix, String correlationId) {
    return "AUDIT-" + prefix + "-" + stableUuid(prefix + "|" + Objects.toString(correlationId, "")).substring(0, 12);
  }

  private static <T> List<T> safeList(List<T> values) {
    return values == null ? List.of() : List.copyOf(values);
  }

  private static Map<String, String> safeMap(Map<String, String> values) {
    return values == null ? Map.of() : Map.copyOf(values);
  }

  public static final class Store {
    public List<ExceptionModels.ExceptionRequestRecord> exceptionRequestRecords = new ArrayList<>();
    public List<ExceptionModels.PricingConcessionRequestRecord> concessionRequestRecords = new ArrayList<>();
    public List<ExceptionModels.ApprovalDecisionRecord> approvalDecisionRecords = new ArrayList<>();
    public List<ExceptionModels.ConcessionApplicationRecord> concessionApplicationRecords = new ArrayList<>();
    public List<ExceptionModels.EligibilityExceptionRequestRecord> eligibilityExceptionRequestRecords = new ArrayList<>();
    public List<ExceptionModels.MonitoringSignalRecord> monitoringSignalRecords = new ArrayList<>();
    public List<ExceptionModels.MonitoringAlertRecord> monitoringAlertRecords = new ArrayList<>();
    public List<ExceptionModels.RiskMonitoringEventRecord> riskMonitoringEventRecords = new ArrayList<>();
    public List<ExceptionModels.AlertDispositionRecord> alertDispositionRecords = new ArrayList<>();
    public List<ExceptionModels.ManualPriceEditAttemptRecord> manualPriceEditAttemptRecords = new ArrayList<>();
    public List<ExceptionModels.AuthorityMatrixVersionRecord> authorityMatrixVersionRecords = new ArrayList<>();
    public List<ExceptionModels.ExceptionHistoryProjectionRecord> historyProjectionRecords = new ArrayList<>();
    public List<ExceptionModels.ExceptionHistoryReplayResult> historyReplayRecords = new ArrayList<>();
    public List<ExceptionModels.ExceptionHistoryExportPacket> historyExportRecords = new ArrayList<>();
    public List<ExceptionModels.ExceptionHistoryAuditRecord> historyAuditRecords = new ArrayList<>();

    private transient LinkedHashMap<String, ExceptionModels.ExceptionRequestRecord> exceptionRequests = new LinkedHashMap<>();
    private transient LinkedHashMap<String, ExceptionModels.PricingConcessionRequestRecord> concessionRequests = new LinkedHashMap<>();
    private transient LinkedHashMap<String, ExceptionModels.ApprovalDecisionRecord> approvalDecisions = new LinkedHashMap<>();
    private transient LinkedHashMap<String, ExceptionModels.ConcessionApplicationRecord> concessionApplications = new LinkedHashMap<>();
    private transient LinkedHashMap<String, ExceptionModels.EligibilityExceptionRequestRecord> eligibilityExceptionRequests = new LinkedHashMap<>();
    private transient LinkedHashMap<String, ExceptionModels.MonitoringSignalRecord> monitoringSignals = new LinkedHashMap<>();
    private transient LinkedHashMap<String, ExceptionModels.MonitoringAlertRecord> monitoringAlerts = new LinkedHashMap<>();
    private transient LinkedHashMap<String, ExceptionModels.RiskMonitoringEventRecord> riskMonitoringEvents = new LinkedHashMap<>();
    private transient LinkedHashMap<String, ExceptionModels.AlertDispositionRecord> alertDispositions = new LinkedHashMap<>();
    private transient LinkedHashMap<String, ExceptionModels.ManualPriceEditAttemptRecord> manualPriceEditAttempts = new LinkedHashMap<>();
    private transient LinkedHashMap<String, ExceptionModels.AuthorityMatrixVersionRecord> authorityMatrixVersions = new LinkedHashMap<>();
    private transient LinkedHashMap<String, ExceptionModels.ExceptionHistoryProjectionRecord> historyProjections = new LinkedHashMap<>();
    private transient LinkedHashMap<String, ExceptionModels.ExceptionHistoryReplayResult> historyReplays = new LinkedHashMap<>();
    private transient LinkedHashMap<String, ExceptionModels.ExceptionHistoryExportPacket> historyExports = new LinkedHashMap<>();
    private transient LinkedHashMap<String, ExceptionModels.ExceptionHistoryAuditRecord> historyAudits = new LinkedHashMap<>();

    private Store reindexed() {
      exceptionRequests = index(exceptionRequestRecords, ExceptionModels.ExceptionRequestRecord::exceptionRequestId);
      concessionRequests = index(concessionRequestRecords, ExceptionModels.PricingConcessionRequestRecord::concessionRequestId);
      approvalDecisions = index(approvalDecisionRecords, ExceptionModels.ApprovalDecisionRecord::decisionId);
      concessionApplications = index(concessionApplicationRecords, ExceptionModels.ConcessionApplicationRecord::applicationId);
      eligibilityExceptionRequests = index(eligibilityExceptionRequestRecords, ExceptionModels.EligibilityExceptionRequestRecord::exceptionRequestId);
      monitoringSignals = index(monitoringSignalRecords, ExceptionModels.MonitoringSignalRecord::signalId);
      monitoringAlerts = index(monitoringAlertRecords, ExceptionModels.MonitoringAlertRecord::alertId);
      riskMonitoringEvents = index(riskMonitoringEventRecords, ExceptionModels.RiskMonitoringEventRecord::riskEventId);
      alertDispositions = index(alertDispositionRecords, ExceptionModels.AlertDispositionRecord::dispositionId);
      manualPriceEditAttempts = index(manualPriceEditAttemptRecords, ExceptionModels.ManualPriceEditAttemptRecord::attemptId);
      authorityMatrixVersions = index(authorityMatrixVersionRecords, ExceptionModels.AuthorityMatrixVersionRecord::matrixVersionId);
      historyProjections = index(historyProjectionRecords, ExceptionModels.ExceptionHistoryProjectionRecord::projectionId);
      historyReplays = index(historyReplayRecords, ExceptionModels.ExceptionHistoryReplayResult::replayId);
      historyExports = index(historyExportRecords, export -> export.manifest().exportId());
      historyAudits = index(historyAuditRecords, ExceptionModels.ExceptionHistoryAuditRecord::auditId);
      return this;
    }

    private Store snapshot() {
      exceptionRequestRecords = new ArrayList<>(exceptionRequests.values());
      concessionRequestRecords = new ArrayList<>(concessionRequests.values());
      approvalDecisionRecords = new ArrayList<>(approvalDecisions.values());
      concessionApplicationRecords = new ArrayList<>(concessionApplications.values());
      eligibilityExceptionRequestRecords = new ArrayList<>(eligibilityExceptionRequests.values());
      monitoringSignalRecords = new ArrayList<>(monitoringSignals.values());
      monitoringAlertRecords = new ArrayList<>(monitoringAlerts.values());
      riskMonitoringEventRecords = new ArrayList<>(riskMonitoringEvents.values());
      alertDispositionRecords = new ArrayList<>(alertDispositions.values());
      manualPriceEditAttemptRecords = new ArrayList<>(manualPriceEditAttempts.values());
      authorityMatrixVersionRecords = new ArrayList<>(authorityMatrixVersions.values());
      historyProjectionRecords = new ArrayList<>(historyProjections.values());
      historyReplayRecords = new ArrayList<>(historyReplays.values());
      historyExportRecords = new ArrayList<>(historyExports.values());
      historyAuditRecords = new ArrayList<>(historyAudits.values());
      return this;
    }

    private static <T> LinkedHashMap<String, T> index(List<T> records, java.util.function.Function<T, String> key) {
      LinkedHashMap<String, T> indexed = new LinkedHashMap<>();
      if (records != null) {
        for (T record : records) {
          indexed.put(key.apply(record), record);
        }
      }
      return indexed;
    }
  }
}
