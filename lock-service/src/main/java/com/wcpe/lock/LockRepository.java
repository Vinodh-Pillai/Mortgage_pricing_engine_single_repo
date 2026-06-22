package com.wcpe.lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

class LockRepository {
  Optional<LockModels.LockRequestResponse> findIdempotency(UUID tenantId, String idempotencyKey, String requestHash) {
    return unavailable();
  }

  boolean hasActiveQuote(UUID tenantId, String quoteId) {
    return unavailable();
  }

  void saveCommitted(
    LockModels.RateLockRecord record,
    LockModels.LockRequestResponse response,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    unavailable();
  }

  Optional<LockModels.RateLockRecord> find(UUID tenantId, String lockId) {
    return unavailable();
  }

  void replace(LockModels.RateLockRecord record) {
    unavailable();
  }

  Optional<LockModels.LockDecisionResponse> findDecisionIdempotency(UUID tenantId, String idempotencyKey, String decisionHash) {
    return unavailable();
  }

  void saveDecision(
    LockModels.RateLockRecord record,
    LockModels.LockDecisionResponse response,
    String idempotencyKey,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    unavailable();
  }

  Optional<LockModels.FreshnessCheckResponse> findFreshnessIdempotency(UUID tenantId, String idempotencyKey, String resultHash) {
    return unavailable();
  }

  void saveFreshnessCheck(
    LockModels.FreshnessCheckRecord record,
    LockModels.FreshnessCheckResponse response,
    String idempotencyKey,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    unavailable();
  }

  Optional<LockModels.FreshnessCheckRecord> findFreshnessCheck(UUID tenantId, String checkId) {
    return unavailable();
  }

  Optional<LockModels.LockConfirmationResponse> findConfirmationIdempotency(UUID tenantId, String idempotencyKey, String confirmationHash) {
    return unavailable();
  }

  Optional<LockModels.LockExtensionResponse> findExtensionIdempotency(UUID tenantId, String idempotencyKey, String resultHash) {
    return unavailable();
  }

  Optional<LockModels.RelockResponse> findRelockIdempotency(UUID tenantId, String idempotencyKey, String resultHash) {
    return unavailable();
  }

  boolean hasActiveConfirmation(UUID tenantId, String lockId) {
    return unavailable();
  }

  boolean hasLockNumber(UUID tenantId, String lockNumber) {
    return unavailable();
  }

  boolean hasInvestorExternalRef(UUID tenantId, String investorId, String investorConfirmationRef) {
    return unavailable();
  }

  void saveConfirmation(
    LockModels.RateLockRecord lockRecord,
    LockModels.LockConfirmationRecord confirmation,
    LockModels.LockConfirmationResponse response,
    String confirmationHash,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    unavailable();
  }

  Optional<LockModels.LockConfirmationRecord> findConfirmation(UUID tenantId, String confirmationId) {
    return unavailable();
  }

  boolean hasOpenExtension(UUID tenantId, String lockId) {
    return unavailable();
  }

  boolean hasOpenRelock(UUID tenantId, String sourceLockId) {
    return unavailable();
  }

  Optional<LockModels.LockExtensionRecord> findExtension(UUID tenantId, String extensionId) {
    return unavailable();
  }

  Optional<LockModels.RelockRecord> findRelock(UUID tenantId, String relockId) {
    return unavailable();
  }

  void saveExtensionRequest(
    LockModels.RateLockRecord lockRecord,
    LockModels.LockExtensionRecord extension,
    LockModels.LockExtensionResponse response,
    String resultHash,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    unavailable();
  }

  void saveExtensionUpdate(
    LockModels.RateLockRecord lockRecord,
    LockModels.LockExtensionRecord extension,
    LockModels.LockExtensionResponse response,
    String idempotencyKey,
    String resultHash,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    unavailable();
  }

  void saveRelockRequest(
    LockModels.RateLockRecord sourceLock,
    LockModels.RateLockRecord replacementLock,
    LockModels.RelockRecord relock,
    LockModels.RelockResponse response,
    String resultHash,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    unavailable();
  }

  void saveRelockUpdate(
    LockModels.RateLockRecord sourceLock,
    LockModels.RateLockRecord replacementLock,
    LockModels.RelockRecord relock,
    LockModels.RelockResponse response,
    String idempotencyKey,
    String resultHash,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    unavailable();
  }

  List<LockModels.LockConfirmationRecord> activeConfirmations(UUID tenantId) {
    return unavailable();
  }

  Optional<LockModels.LockExpirationSchedule> findExpirationSchedule(UUID tenantId, String lockId) {
    return unavailable();
  }

  void saveExpirationEvaluation(
    LockModels.RateLockRecord lockRecord,
    LockModels.LockExpirationSchedule schedule,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    unavailable();
  }

  void saveExpirationRun(LockModels.LockExpirationRunRecord runRecord) {
    unavailable();
  }

  Optional<LockModels.LockExpirationRunRecord> findExpirationRun(UUID tenantId, String runId) {
    return unavailable();
  }

  Optional<LockModels.LockSyncAttempt> findSyncAttempt(UUID tenantId, String attemptId) {
    return unavailable();
  }

  Optional<LockModels.LockSyncAttempt> findSyncAttemptByEventTarget(UUID tenantId, String eventId, String targetId) {
    return unavailable();
  }

  Optional<LockModels.LockAuditReportResponse> findAuditReportIdempotency(UUID tenantId, String idempotencyKey, String reportHash) {
    return unavailable();
  }

  void saveAuditReport(
    LockModels.LockAuditReportRecord report,
    LockModels.LockAuditReportResponse response,
    String reportHash,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    unavailable();
  }

  Optional<LockModels.LockAuditReportRecord> findAuditReport(UUID tenantId, String reportId) {
    return unavailable();
  }

  Optional<LockModels.LockReplayResponse> findReplayIdempotency(UUID tenantId, String idempotencyKey, String replayHash) {
    return unavailable();
  }

  void saveReplayResult(
    LockModels.LockReplayResult result,
    LockModels.LockReplayResponse response,
    String replayHash,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    unavailable();
  }

  Optional<LockModels.LockReplayResult> findReplayResult(UUID tenantId, String replayId) {
    return unavailable();
  }

  Optional<LockModels.LockCancellationResponse> findCancellationIdempotency(UUID tenantId, String idempotencyKey, String cancellationHash) {
    return unavailable();
  }

  void saveCancellation(
    LockModels.RateLockRecord lockRecord,
    LockModels.LockCancellationRecord cancellation,
    LockModels.LockCancellationResponse response,
    String cancellationHash,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    unavailable();
  }

  Optional<LockModels.LockCancellationRecord> findCancellation(UUID tenantId, String cancellationId) {
    return unavailable();
  }

  Optional<LockModels.LockEvidenceExportResponse> findEvidenceExportIdempotency(UUID tenantId, String idempotencyKey, String manifestHash) {
    return unavailable();
  }

  void saveEvidenceExport(
    LockModels.LockEvidenceExportRecord export,
    LockModels.LockEvidenceExportResponse response,
    String manifestHash,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    unavailable();
  }

  Optional<LockModels.LockEvidenceExportRecord> findEvidenceExport(UUID tenantId, String exportId) {
    return unavailable();
  }

  List<LockModels.LockSyncAttempt> syncAttemptsForLock(UUID tenantId, String lockId) {
    return unavailable();
  }

  void saveSyncAttempt(
    LockModels.LockSyncAttempt attempt,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    unavailable();
  }

  void saveSyncAcknowledgement(
    LockModels.RateLockRecord lockRecord,
    LockModels.LockSyncAttempt attempt,
    LockModels.LockSyncAcknowledgement acknowledgement,
    LockModels.LockReconciliationRecord reconciliation,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    unavailable();
  }

  Optional<LockModels.LockSyncAcknowledgement> findSyncAcknowledgement(UUID tenantId, String ackId) {
    return unavailable();
  }

  Optional<LockModels.LockReconciliationRecord> findReconciliation(UUID tenantId, String recordId) {
    return unavailable();
  }

  int lockCount() {
    return unavailable();
  }

  int freshnessCheckCount() {
    return unavailable();
  }

  int confirmationCount() {
    return unavailable();
  }

  int expirationRunCount() {
    return unavailable();
  }

  int extensionCount() {
    return unavailable();
  }

  int relockCount() {
    return unavailable();
  }

  int syncAttemptCount() {
    return unavailable();
  }

  int syncAcknowledgementCount() {
    return unavailable();
  }

  int reconciliationCount() {
    return unavailable();
  }

  int auditReportCount() {
    return unavailable();
  }

  int replayResultCount() {
    return unavailable();
  }

  int cancellationCount() {
    return unavailable();
  }

  int evidenceExportCount() {
    return unavailable();
  }

  List<LockModels.LockEvent> outboxEvents() {
    return unavailable();
  }

  List<LockModels.AuditSnapshot> auditSnapshots() {
    return unavailable();
  }

  private static <T> T unavailable() {
    throw new LockServiceException(
      "PERSISTENCE_NOT_DURABLE",
      "Durable lock repository is not wired; production lock state cannot use process-local storage."
    );
  }
}
