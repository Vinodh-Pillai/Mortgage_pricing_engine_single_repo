package com.wcpe.exception.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fail-closed repository boundary for exception lifecycle state.
 *
 * <p>The prior implementation used in-memory maps as the source of truth for exception,
 * concession, approval, application, monitoring, authority-matrix, manual-edit, and history state.
 * This service has SQL migration artifacts, but no wired durable repository implementation in this
 * module. Until a durable adapter is introduced, every repository operation fails explicitly rather
 * than accepting writes or returning state from process-local memory.</p>
 */
public class ExceptionRepository {

  private static final String PERSISTENCE_BACKEND_REQUIRED = "PERSISTENCE_BACKEND_REQUIRED";
  private static final String PERSISTENCE_BACKEND_MESSAGE =
    "exception-service requires a durable persistence repository; in-memory source-of-truth is disabled";

  public ExceptionModels.ExceptionRequestRecord create(ExceptionModels.ExceptionRequestCreate request) {
    throw unavailable();
  }

  public Optional<ExceptionModels.ExceptionRequestRecord> findById(String id) {
    throw unavailable();
  }

  public Optional<ExceptionModels.PricingConcessionRequestRecord> findConcessionById(String tenantId, String id) {
    throw unavailable();
  }

  public Optional<ExceptionModels.PricingConcessionRequestRecord> findConcessionByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    throw unavailable();
  }

  public Optional<ExceptionModels.EligibilityExceptionRequestRecord> findEligibilityExceptionById(
    UUID tenantId,
    String id
  ) {
    throw unavailable();
  }

  public Optional<ExceptionModels.EligibilityExceptionRequestRecord> findEligibilityExceptionByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    throw unavailable();
  }

  public Optional<ExceptionModels.EligibilityExceptionRequestRecord> findActiveEligibilityException(
    UUID tenantId,
    ExceptionModels.EligibilityFindingRef findingRef,
    ExceptionModels.EligibilityExceptionScope exceptionScope
  ) {
    throw unavailable();
  }

  public ExceptionModels.EligibilityExceptionRequestRecord createEligibilityException(
    ExceptionModels.CreateEligibilityExceptionRequest request,
    ExceptionModels.EligibilityExceptionPolicy policy,
    String narrativeRedacted,
    String approvalRouteHash,
    String requestHash,
    String eventHash
  ) {
    throw unavailable();
  }

  public ExceptionModels.PricingConcessionRequestRecord createConcession(
    ExceptionModels.PricingConcessionRequestCreate request,
    ExceptionModels.ConcessionRequestStatus status,
    String commentsRedacted,
    String approvalRouteHash,
    String requestHash
  ) {
    throw unavailable();
  }

  public Optional<ExceptionModels.ApprovalDecisionRecord> findApprovalByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    throw unavailable();
  }

  public ExceptionModels.ApprovalDecisionRecord approveConcession(
    ExceptionModels.PricingConcessionRequestRecord request,
    ExceptionModels.ApproveConcessionRequest approval,
    String commentRedacted,
    String eventHash
  ) {
    throw unavailable();
  }

  public Optional<ExceptionModels.ConcessionApplicationRecord> findApplicationByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    throw unavailable();
  }

  public Optional<ExceptionModels.ConcessionApplicationRecord> findApplicationByTarget(
    UUID tenantId,
    String concessionRequestId,
    ExceptionModels.ApplicationTarget target
  ) {
    throw unavailable();
  }

  public ExceptionModels.ConcessionApplicationRecord applyConcession(
    ExceptionModels.PricingConcessionRequestRecord request,
    ExceptionModels.ApplyApprovedConcessionRequest command,
    String pricingLedgerEntryId,
    String afterPriceHash,
    String replayHash,
    String outboxEventType
  ) {
    throw unavailable();
  }

  public Optional<ExceptionModels.MonitoringSignalRecord> findMonitoringSignalByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    throw unavailable();
  }

  public Optional<ExceptionModels.MonitoringAlertRecord> findMonitoringAlertBySignalId(String signalId) {
    throw unavailable();
  }

  public Optional<ExceptionModels.MonitoringAlertRecord> findMonitoringAlertById(UUID tenantId, String alertId) {
    throw unavailable();
  }

  public Optional<ExceptionModels.RiskMonitoringEventRecord> findRiskMonitoringEventByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    throw unavailable();
  }

  public Optional<ExceptionModels.RiskMonitoringEventRecord> findRiskMonitoringEventBySourceEvent(
    UUID tenantId,
    String sourceEventId
  ) {
    throw unavailable();
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
    throw unavailable();
  }

  public ExceptionModels.MonitoringSignalRecord recordMonitoringSignal(
    ExceptionModels.ConcessionMonitoringSignalCommand command,
    ExceptionModels.MonitoringPolicyVersion policy,
    String signalHash
  ) {
    throw unavailable();
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
    throw unavailable();
  }

  public Optional<ExceptionModels.AlertDispositionRecord> findAlertDispositionByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    throw unavailable();
  }

  public ExceptionModels.AlertDispositionRecord dispositionMonitoringAlert(
    ExceptionModels.MonitoringAlertRecord alert,
    ExceptionModels.ConcessionAlertDispositionCommand command,
    ExceptionModels.AlertStatus newStatus,
    String commentRedacted,
    String dispositionHash
  ) {
    throw unavailable();
  }

  public Optional<ExceptionModels.AuthorityMatrixVersionRecord> findAuthorityMatrixById(
    UUID tenantId,
    String matrixVersionId
  ) {
    throw unavailable();
  }

  public Optional<ExceptionModels.AuthorityMatrixVersionRecord> findAuthorityMatrixByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    throw unavailable();
  }

  public Optional<ExceptionModels.ManualPriceEditAttemptRecord> findManualPriceEditAttemptByIdempotencyKey(
    UUID tenantId,
    String idempotencyKey
  ) {
    throw unavailable();
  }

  public Optional<ExceptionModels.ManualPriceEditAttemptRecord> findManualPriceEditAttemptById(
    UUID tenantId,
    String attemptId
  ) {
    throw unavailable();
  }

  public ExceptionModels.ExceptionHistoryProjectionRecord saveHistoryProjection(
    ExceptionModels.ExceptionHistoryTimeline timeline
  ) {
    throw unavailable();
  }

  public Optional<ExceptionModels.ExceptionHistoryProjectionRecord> findHistoryProjection(
    UUID tenantId,
    ExceptionModels.ExceptionHistorySubjectType subjectType,
    String subjectId
  ) {
    throw unavailable();
  }

  public ExceptionModels.ExceptionHistoryReplayResult saveHistoryReplay(
    ExceptionModels.ExceptionHistoryReplayResult replay) {
    throw unavailable();
  }

  public Optional<ExceptionModels.ExceptionHistoryReplayResult> findHistoryReplay(UUID tenantId, String replayId) {
    throw unavailable();
  }

  public ExceptionModels.ExceptionHistoryExportPacket saveHistoryExport(
    ExceptionModels.ExceptionHistoryExportPacket export
  ) {
    throw unavailable();
  }

  public Optional<ExceptionModels.ExceptionHistoryExportPacket> findHistoryExport(UUID tenantId, String exportId) {
    throw unavailable();
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
    throw unavailable();
  }

  public ExceptionModels.ManualPriceEditAttemptRecord recordManualPriceEditAttempt(
    ExceptionModels.GuardPriceMutationCommand command,
    List<String> guardedFields,
    String payloadHash,
    String denialReason,
    String policyVersionId,
    String eventHash
  ) {
    throw unavailable();
  }

  public ExceptionModels.AuthorityMatrixVersionRecord createAuthorityMatrixDraft(
    ExceptionModels.CreateAuthorityMatrixDraftCommand command,
    List<ExceptionModels.AuthorityMatrixRuleDraft> normalizedRules,
    List<ExceptionModels.AuthorityMatrixValidationMessage> validationMessages,
    String validationHash,
    String requestHash
  ) {
    throw unavailable();
  }

  public ExceptionModels.AuthorityMatrixVersionRecord saveAuthorityMatrixVersion(
    ExceptionModels.AuthorityMatrixVersionRecord record
  ) {
    throw unavailable();
  }

  public void suspendPublishedAuthorityMatrices(UUID tenantId, String exceptMatrixVersionId, Instant updatedAt) {
    throw unavailable();
  }

  public Optional<ExceptionModels.AuthorityMatrixVersionRecord> findPublishedAuthorityMatrix(
    UUID tenantId,
    Instant effectiveAt
  ) {
    throw unavailable();
  }

  public Optional<ExceptionModels.ExceptionRequestRecord> transition(String id, ExceptionModels.ExceptionState target) {
    throw unavailable();
  }

  public List<ExceptionModels.ExceptionRequestRecord> exceptionRequests() {
    throw unavailable();
  }

  public List<ExceptionModels.PricingConcessionRequestRecord> concessionRequests() {
    throw unavailable();
  }

  public List<ExceptionModels.ApprovalDecisionRecord> approvalDecisions() {
    throw unavailable();
  }

  public List<ExceptionModels.ConcessionApplicationRecord> concessionApplications() {
    throw unavailable();
  }

  public List<ExceptionModels.EligibilityExceptionRequestRecord> eligibilityExceptionRequests() {
    throw unavailable();
  }

  public List<ExceptionModels.MonitoringSignalRecord> monitoringSignals() {
    throw unavailable();
  }

  public List<ExceptionModels.MonitoringAlertRecord> monitoringAlerts() {
    throw unavailable();
  }

  public List<ExceptionModels.RiskMonitoringEventRecord> riskMonitoringEvents() {
    throw unavailable();
  }

  public List<ExceptionModels.AlertDispositionRecord> alertDispositions() {
    throw unavailable();
  }

  public List<ExceptionModels.ManualPriceEditAttemptRecord> manualPriceEditAttempts() {
    throw unavailable();
  }

  public List<ExceptionModels.ExceptionHistoryProjectionRecord> historyProjections() {
    throw unavailable();
  }

  public List<ExceptionModels.ExceptionHistoryReplayResult> historyReplays() {
    throw unavailable();
  }

  public List<ExceptionModels.ExceptionHistoryExportPacket> historyExports() {
    throw unavailable();
  }

  public List<ExceptionModels.ExceptionHistoryAuditRecord> historyAudits() {
    throw unavailable();
  }

  void clear() {
    throw unavailable();
  }

  private static ExceptionServiceException unavailable() {
    return new ExceptionServiceException(PERSISTENCE_BACKEND_REQUIRED, PERSISTENCE_BACKEND_MESSAGE);
  }
}
