package com.wcpe.lock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

final class JdbcLockRepository extends LockRepository {
  private static final List<String> ACTIVE_STATUSES = List.of(
    "REQUESTED", "PENDING_APPROVAL", "APPROVED", "PENDING_INVESTOR_CONFIRMATION", "ACTIVE", "EXPIRING_SOON",
    "EXTENSION_REQUESTED", "EXTENSION_APPROVED", "PENDING_INVESTOR_EXTENSION_CONFIRMATION",
    "RELOCK_REQUESTED", "RELOCK_APPROVED", "PENDING_INVESTOR_RELOCK_CONFIRMATION"
  );

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  JdbcLockRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc is required");
    this.mapper = java.util.Objects.requireNonNull(mapper, "mapper is required").findAndRegisterModules();
  }

  @Override
  Optional<LockModels.LockRequestResponse> findIdempotency(UUID tenantId, String idempotencyKey, String requestHash) {
    Optional<LockModels.RateLockRecord> record = findByIdempotency(tenantId, idempotencyKey);
    if (record.isEmpty()) return Optional.empty();
    if (!record.get().requestHash().equals(requestHash)) {
      throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different payload");
    }
    LockModels.RateLockRecord lock = record.get();
    return Optional.of(new LockModels.LockRequestResponse(
      lock.tenantId(), lock.lockId(), lock.requestId(), lock.status(), lock.version(),
      "Lock request accepted using tenant policy configuration", List.of(), lock.auditRef(), lock.replayRef(),
      lock.correlationId(), lock.outboxEventType(), lock.requestHash()
    ));
  }

  @Override
  boolean hasActiveQuote(UUID tenantId, String quoteId) {
    return count("select count(*) from rate_locks where tenant_id = ? and quote_id = ? and status in (" + placeholders(ACTIVE_STATUSES.size()) + ")",
      argsWithStatuses(tenantId, quoteId)) > 0;
  }

  @Override
  void saveCommitted(LockModels.RateLockRecord record, LockModels.LockRequestResponse response, LockModels.LockEvent event, LockModels.AuditSnapshot audit) {
    insertLock(record);
    saveEvent(event);
    saveAudit(audit);
  }

  @Override
  Optional<LockModels.RateLockRecord> find(UUID tenantId, String lockId) {
    return queryOne("select * from rate_locks where tenant_id = ? and lock_id = ?", (rs, rowNum) -> lockRecord(rs), tenantId, lockId);
  }

  @Override
  List<LockModels.RateLockRecord> list(UUID tenantId) {
    return jdbc.query("select * from rate_locks where tenant_id = ? order by updated_at desc, lock_id", (rs, rowNum) -> lockRecord(rs), tenantId);
  }

  @Override
  void replace(LockModels.RateLockRecord record) {
    jdbc.update("""
      update rate_locks
      set status = ?, version = ?, updated_at = ?, expires_at = ?, correlation_id = ?, lock_policy_version_id = ?,
        request_hash = ?, audit_ref = ?, replay_ref = ?
      where tenant_id = ? and lock_id = ?
      """, record.status().name(), record.version(), ts(record.updatedAt()), ts(record.expiresAt()), record.correlationId(),
      record.lockPolicyVersionId(), record.requestHash(), record.auditRef(), record.replayRef(), record.tenantId(), record.lockId());
  }

  @Override
  Optional<LockModels.LockDecisionResponse> findDecisionIdempotency(UUID tenantId, String idempotencyKey, String decisionHash) {
    Optional<LockModels.LockEvent> event = eventByIdempotency(tenantId, idempotencyKey, List.of("lock.approved.v1", "lock.rejected.v1"));
    if (event.isEmpty()) return Optional.empty();
    LockModels.RateLockRecord lock = find(tenantId, event.get().lockId())
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Lock was not found for tenant"));
    LockModels.AuditSnapshot audit = auditByRef(lock.auditRef())
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Lock decision audit was not found for tenant"));
    if (!audit.replayHash().equals(decisionHash)) {
      throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Decision idempotency key was reused with a different payload");
    }
    Map<String, String> payload = event.get().payload();
    LockModels.RateLockStatus previous = LockModels.RateLockStatus.valueOf(payload.get("previousStatus"));
    LockModels.RateLockStatus status = LockModels.RateLockStatus.valueOf(payload.get("status"));
    LockModels.LockDecisionType decision = "lock.approved.v1".equals(event.get().eventType())
      ? LockModels.LockDecisionType.APPROVE
      : LockModels.LockDecisionType.REJECT;
    return Optional.of(new LockModels.LockDecisionResponse(
      tenantId, lock.lockId(), payload.get("decisionId"), decision, previous, status, lock.version(),
      decision == LockModels.LockDecisionType.APPROVE ? "Lock decision approved using current tenant policy configuration" : "Lock decision rejected using configured reason codes",
      List.of(), audit.auditRef(), lock.replayRef(), lock.correlationId(), event.get().eventType(), decisionHash
    ));
  }

  @Override
  void saveDecision(LockModels.RateLockRecord record, LockModels.LockDecisionResponse response, String idempotencyKey, LockModels.LockEvent event, LockModels.AuditSnapshot audit) {
    replace(record);
    saveEvent(event);
    saveAudit(audit);
  }

  @Override
  Optional<LockModels.FreshnessCheckResponse> findFreshnessIdempotency(UUID tenantId, String idempotencyKey, String resultHash) {
    Optional<LockModels.FreshnessCheckRecord> record = queryOne(
      "select * from lock_freshness_checks where tenant_id = ? and idempotency_key = ? order by evaluated_at desc limit 1",
      (rs, rowNum) -> freshnessRecord(rs), tenantId, idempotencyKey);
    if (record.isEmpty()) return Optional.empty();
    if (!record.get().resultHash().equals(resultHash)) {
      throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Freshness idempotency key was reused with a different payload");
    }
    return record.map(value -> new LockModels.FreshnessCheckResponse(
      value.tenantId(), value.checkId(), value.quoteId(), value.decision(), value.reasonCodes(), value.policyVersionId(), 0,
      value.expiresAt(), List.of(), "AUDIT-FRESHNESS-" + value.checkId(), "REPLAY-FRESHNESS-" + value.resultHash().substring(0, 16),
      value.correlationId(), value.resultHash()
    ));
  }

  @Override
  void saveFreshnessCheck(LockModels.FreshnessCheckRecord record, LockModels.FreshnessCheckResponse response, String idempotencyKey, LockModels.LockEvent event, LockModels.AuditSnapshot audit) {
    jdbc.update("""
      insert into lock_freshness_checks (check_id, tenant_id, quote_id, scenario_hash, policy_version, decision, reason_codes,
        evaluated_at, expires_at, result_hash, idempotency_key, created_by, correlation_id)
      values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?)
      """, record.checkId(), record.tenantId(), record.quoteId(), record.scenarioHash(), record.policyVersionId(), record.decision().name(),
      writeJson(record.reasonCodes()), ts(record.evaluatedAt()), ts(record.expiresAt()), record.resultHash(), idempotencyKey, record.createdBy(), record.correlationId());
    saveEvent(event);
    saveAudit(audit);
  }

  @Override
  Optional<LockModels.FreshnessCheckRecord> findFreshnessCheck(UUID tenantId, String checkId) {
    return queryOne("select * from lock_freshness_checks where tenant_id = ? and check_id = ?", (rs, rowNum) -> freshnessRecord(rs), tenantId, checkId);
  }

  @Override
  Optional<LockModels.LockConfirmationResponse> findConfirmationIdempotency(UUID tenantId, String idempotencyKey, String confirmationHash) {
    Optional<LockModels.LockConfirmationRecord> confirmation = queryOne(
      "select * from lock_confirmations where tenant_id = ? and idempotency_key = ? order by confirmed_at desc limit 1",
      (rs, rowNum) -> confirmationRecord(rs), tenantId, idempotencyKey);
    if (confirmation.isEmpty()) return Optional.empty();
    if (!confirmation.get().confirmedTermsHash().equals(confirmationHash)) {
      throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Confirmation idempotency key was reused with a different payload");
    }
    LockModels.RateLockRecord lock = find(tenantId, confirmation.get().lockId())
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Lock was not found for tenant"));
    return Optional.of(confirmationResponse(lock, confirmation.get(), confirmationHash));
  }

  @Override
  Optional<LockModels.LockExtensionResponse> findExtensionIdempotency(UUID tenantId, String idempotencyKey, String resultHash) {
    Optional<LockModels.LockExtensionRecord> extension = queryOne(
      "select * from lock_extensions where tenant_id = ? and idempotency_key = ? order by requested_at desc limit 1",
      (rs, rowNum) -> extensionRecord(rs), tenantId, idempotencyKey);
    if (extension.isEmpty()) return Optional.empty();
    if (!extension.get().costSnapshotHash().equals(resultHash)) {
      throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Extension idempotency key was reused with a different payload");
    }
    LockModels.RateLockRecord lock = find(tenantId, extension.get().lockId())
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Lock was not found for tenant"));
    return Optional.of(extensionResponse(lock, extension.get(), resultHash));
  }

  @Override
  Optional<LockModels.RelockResponse> findRelockIdempotency(UUID tenantId, String idempotencyKey, String resultHash) {
    Optional<LockModels.RelockRecord> relock = queryOne(
      "select * from lock_relocks where tenant_id = ? and idempotency_key = ? order by requested_at desc limit 1",
      (rs, rowNum) -> relockRecord(rs), tenantId, idempotencyKey);
    if (relock.isEmpty()) return Optional.empty();
    if (!relock.get().comparisonHash().equals(resultHash)) {
      throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Relock idempotency key was reused with a different payload");
    }
    LockModels.RateLockRecord source = find(tenantId, relock.get().sourceLockId())
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Source lock was not found for tenant"));
    LockModels.RateLockRecord replacement = find(tenantId, relock.get().replacementLockId()).orElse(source);
    return Optional.of(relockResponse(source, replacement, relock.get()));
  }

  @Override
  boolean hasActiveConfirmation(UUID tenantId, String lockId) {
    return count("select count(*) from lock_confirmations where tenant_id = ? and lock_id = ? and status = 'ACTIVE'", tenantId, lockId) > 0;
  }

  @Override
  boolean hasLockNumber(UUID tenantId, String lockNumber) {
    if (lockNumber == null || lockNumber.isBlank()) return false;
    return count("select count(*) from lock_confirmations where tenant_id = ? and lock_number = ? and status = 'ACTIVE'", tenantId, lockNumber) > 0;
  }

  @Override
  boolean hasInvestorExternalRef(UUID tenantId, String investorId, String investorConfirmationRef) {
    if (investorId == null || investorConfirmationRef == null || investorId.isBlank() || investorConfirmationRef.isBlank()) return false;
    return count("select count(*) from lock_confirmations where tenant_id = ? and investor_id = ? and investor_confirmation_ref = ? and status = 'ACTIVE'",
      tenantId, investorId, investorConfirmationRef) > 0;
  }

  @Override
  void saveConfirmation(LockModels.RateLockRecord lockRecord, LockModels.LockConfirmationRecord confirmation, LockModels.LockConfirmationResponse response, String confirmationHash, LockModels.LockEvent event, LockModels.AuditSnapshot audit) {
    replace(lockRecord);
    jdbc.update("""
      insert into lock_confirmations (tenant_id, confirmation_id, lock_id, confirmation_type, lock_number, investor_id,
        investor_confirmation_ref, status, lock_version, confirmed_at, expires_at, confirmed_terms_hash, idempotency_key,
        correlation_id, replay_ref)
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """, confirmation.tenantId(), confirmation.confirmationId(), confirmation.lockId(), confirmation.confirmationType().name(),
      confirmation.lockNumber(), confirmation.investorId(), confirmation.investorConfirmationRef(), confirmation.status().name(), confirmation.lockVersion(),
      ts(confirmation.confirmedAt()), ts(confirmation.expiresAt()), confirmationHash, confirmation.idempotencyKey(), confirmation.correlationId(), confirmation.replayRef());
    if (confirmation.status() == LockModels.RateLockStatus.ACTIVE) {
      upsertExpirationSchedule(new LockModels.LockExpirationSchedule(confirmation.tenantId(), confirmation.lockId(), confirmation.expiresAt(),
        confirmation.expirationBusinessDays(), confirmation.expirationCalculatedAt(), confirmation.calendarConfigHash(), confirmation.expirationBreakdown(),
        null, lockRecord.lockPolicyVersionId(), null));
    }
    saveEvent(event);
    saveAudit(audit);
  }

  @Override
  Optional<LockModels.LockConfirmationRecord> findConfirmation(UUID tenantId, String confirmationId) {
    return queryOne("select * from lock_confirmations where tenant_id = ? and confirmation_id = ?", (rs, rowNum) -> confirmationRecord(rs), tenantId, confirmationId);
  }

  @Override
  boolean hasOpenExtension(UUID tenantId, String lockId) {
    return count("select count(*) from lock_extensions where tenant_id = ? and lock_id = ? and status in ('REQUESTED', 'APPROVED', 'PENDING_INVESTOR_CONFIRMATION')", tenantId, lockId) > 0;
  }

  @Override
  boolean hasOpenRelock(UUID tenantId, String sourceLockId) {
    return count("select count(*) from lock_relocks where tenant_id = ? and source_lock_id = ? and status in ('REQUESTED', 'APPROVED', 'PENDING_INVESTOR_CONFIRMATION')", tenantId, sourceLockId) > 0;
  }

  @Override
  Optional<LockModels.LockExtensionRecord> findExtension(UUID tenantId, String extensionId) {
    return queryOne("select * from lock_extensions where tenant_id = ? and extension_id = ?", (rs, rowNum) -> extensionRecord(rs), tenantId, extensionId);
  }

  @Override
  Optional<LockModels.RelockRecord> findRelock(UUID tenantId, String relockId) {
    return queryOne("select * from lock_relocks where tenant_id = ? and relock_id = ?", (rs, rowNum) -> relockRecord(rs), tenantId, relockId);
  }

  @Override
  void saveExtensionRequest(LockModels.RateLockRecord lockRecord, LockModels.LockExtensionRecord extension, LockModels.LockExtensionResponse response, String resultHash, LockModels.LockEvent event, LockModels.AuditSnapshot audit) {
    replace(lockRecord);
    upsertExtension(extension, response.costSnapshot(), resultHash);
    saveEvent(event);
    saveAudit(audit);
  }

  @Override
  void saveExtensionUpdate(LockModels.RateLockRecord lockRecord, LockModels.LockExtensionRecord extension, LockModels.LockExtensionResponse response, String idempotencyKey, String resultHash, LockModels.LockEvent event, LockModels.AuditSnapshot audit) {
    replace(lockRecord);
    upsertExtension(new LockModels.LockExtensionRecord(extension.tenantId(), extension.extensionId(), extension.lockId(), extension.status(), extension.lockVersion(),
      extension.requestedDays(), extension.previousExpiresAt(), extension.requestedExpiresAt(), extension.reasonCode(), extension.requestedBy(), extension.approvedBy(),
      extension.requestedAt(), extension.decidedAt(), extension.confirmedAt(), extension.policyVersionId(), resultHash, idempotencyKey, extension.correlationId(), extension.replayRef()),
      response.costSnapshot(), resultHash);
    if (extension.status() == LockModels.LockExtensionStatus.CONFIRMED && lockRecord.status() == LockModels.RateLockStatus.ACTIVE) {
      upsertExpirationSchedule(new LockModels.LockExpirationSchedule(extension.tenantId(), extension.lockId(), extension.requestedExpiresAt(), response.requestedDays(),
        extension.confirmedAt(), response.calendarConfigHash(), response.expirationBreakdown(), null, extension.policyVersionId(), null));
    }
    saveEvent(event);
    saveAudit(audit);
  }

  @Override
  void saveRelockRequest(LockModels.RateLockRecord sourceLock, LockModels.RateLockRecord replacementLock, LockModels.RelockRecord relock, LockModels.RelockResponse response, String resultHash, LockModels.LockEvent event, LockModels.AuditSnapshot audit) {
    replace(sourceLock);
    insertLock(replacementLock);
    upsertRelock(relock, resultHash);
    saveEvent(event);
    saveAudit(audit);
  }

  @Override
  void saveRelockUpdate(LockModels.RateLockRecord sourceLock, LockModels.RateLockRecord replacementLock, LockModels.RelockRecord relock, LockModels.RelockResponse response, String idempotencyKey, String resultHash, LockModels.LockEvent event, LockModels.AuditSnapshot audit) {
    replace(sourceLock);
    replace(replacementLock);
    upsertRelock(new LockModels.RelockRecord(relock.tenantId(), relock.relockId(), relock.sourceLockId(), relock.replacementLockId(), relock.currentQuoteId(),
      relock.status(), relock.sourceLockVersion(), relock.requestedBy(), relock.approvedBy(), relock.requestedAt(), relock.decidedAt(), relock.confirmedAt(),
      relock.reasonCode(), relock.policyVersionId(), relock.investorConfirmationRequired(), resultHash, idempotencyKey, relock.correlationId(), relock.replayRef()), resultHash);
    saveEvent(event);
    saveAudit(audit);
  }

  @Override
  List<LockModels.LockConfirmationRecord> activeConfirmations(UUID tenantId) {
    return jdbc.query("select * from lock_confirmations where tenant_id = ? and status = 'ACTIVE' order by expires_at, confirmation_id", (rs, rowNum) -> confirmationRecord(rs), tenantId);
  }

  @Override
  Optional<LockModels.LockExpirationSchedule> findExpirationSchedule(UUID tenantId, String lockId) {
    return queryOne("select * from lock_expiration_schedules where tenant_id = ? and lock_id = ?", (rs, rowNum) -> expirationSchedule(rs), tenantId, lockId);
  }

  @Override
  void saveExpirationEvaluation(LockModels.RateLockRecord lockRecord, LockModels.LockExpirationSchedule schedule, LockModels.LockEvent event, LockModels.AuditSnapshot audit) {
    replace(lockRecord);
    upsertExpirationSchedule(schedule);
    saveEvent(event);
    saveAudit(audit);
  }

  @Override
  void saveExpirationRun(LockModels.LockExpirationRunRecord runRecord) {
    jdbc.update("""
      insert into lock_expiration_runs (tenant_id, run_id, started_at, completed_at, status, processed_count, expiring_soon_count,
        expired_count, no_op_count, replay_ref, correlation_id)
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      on conflict (run_id) do update set completed_at = excluded.completed_at, status = excluded.status,
        processed_count = excluded.processed_count, expiring_soon_count = excluded.expiring_soon_count,
        expired_count = excluded.expired_count, no_op_count = excluded.no_op_count
      """, runRecord.tenantId(), runRecord.runId(), ts(runRecord.startedAt()), ts(runRecord.completedAt()), runRecord.status(),
      runRecord.processedCount(), runRecord.expiringSoonCount(), runRecord.expiredCount(), runRecord.noOpCount(), runRecord.replayRef(), runRecord.correlationId());
  }

  @Override
  Optional<LockModels.LockExpirationRunRecord> findExpirationRun(UUID tenantId, String runId) {
    return queryOne("select * from lock_expiration_runs where tenant_id = ? and run_id = ?", (rs, rowNum) -> new LockModels.LockExpirationRunRecord(
      (UUID) rs.getObject("tenant_id"), rs.getString("run_id"), instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("completed_at")),
      rs.getString("status"), rs.getInt("processed_count"), rs.getInt("expiring_soon_count"), rs.getInt("expired_count"), rs.getInt("no_op_count"),
      rs.getString("replay_ref"), rs.getString("correlation_id")), tenantId, runId);
  }

  @Override
  Optional<LockModels.LockSyncAttempt> findSyncAttempt(UUID tenantId, String attemptId) {
    return queryOne("select * from lock_sync_attempts where tenant_id = ? and attempt_id = ?", (rs, rowNum) -> syncAttempt(rs), tenantId, attemptId);
  }

  @Override
  Optional<LockModels.LockSyncAttempt> findSyncAttemptByEventTarget(UUID tenantId, String eventId, String targetId) {
    return queryOne("select * from lock_sync_attempts where tenant_id = ? and event_id = ? and target_id = ?", (rs, rowNum) -> syncAttempt(rs), tenantId, eventId, targetId);
  }

  @Override
  void saveAuditReport(LockModels.LockAuditReportRecord report, LockModels.LockAuditReportResponse response, String reportHash, LockModels.LockEvent event, LockModels.AuditSnapshot audit) {
    jdbc.update("""
      insert into lock_audit_reports (tenant_id, report_id, status, requested_by, generated_at, criteria_hash, manifest_hash, idempotency_key, correlation_id)
      values (?, ?, ?, ?, ?, ?, ?, ?, ?)
      """, report.tenantId(), report.reportId(), report.status().name(), report.requestedBy(), ts(report.generatedAt()),
      report.criteriaHash(), report.manifestHash(), report.idempotencyKey(), report.correlationId());
    saveEvent(event);
    saveAudit(audit);
  }

  @Override
  Optional<LockModels.LockAuditReportRecord> findAuditReport(UUID tenantId, String reportId) {
    return queryOne("select * from lock_audit_reports where tenant_id = ? and report_id = ?", (rs, rowNum) -> new LockModels.LockAuditReportRecord(
      (UUID) rs.getObject("tenant_id"), rs.getString("report_id"), LockModels.LockAuditReportStatus.valueOf(rs.getString("status")),
      rs.getString("requested_by"), instant(rs.getTimestamp("generated_at")), rs.getString("criteria_hash"), rs.getString("manifest_hash"),
      rs.getString("idempotency_key"), rs.getString("correlation_id")
    ), tenantId, reportId);
  }

  @Override
  Optional<LockModels.LockAuditReportResponse> findAuditReportIdempotency(UUID tenantId, String idempotencyKey, String reportHash) {
    Optional<LockModels.LockAuditReportRecord> report = queryOne("select * from lock_audit_reports where tenant_id = ? and idempotency_key = ?", (rs, rowNum) -> new LockModels.LockAuditReportRecord(
      (UUID) rs.getObject("tenant_id"), rs.getString("report_id"), LockModels.LockAuditReportStatus.valueOf(rs.getString("status")),
      rs.getString("requested_by"), instant(rs.getTimestamp("generated_at")), rs.getString("criteria_hash"), rs.getString("manifest_hash"),
      rs.getString("idempotency_key"), rs.getString("correlation_id")
    ), tenantId, idempotencyKey);
    if (report.isEmpty()) return Optional.empty();
    if (!report.get().criteriaHash().equals(reportHash)) {
      throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Audit report idempotency key was reused with a different payload");
    }
    LockModels.LockAuditReportRecord value = report.get();
    return Optional.of(new LockModels.LockAuditReportResponse(value.tenantId(), value.reportId(), value.status(), 1,
      "Lock audit report metadata persisted without changing historical lock facts", List.of(), "AUDIT-LOCK-REPORT-" + value.reportId(),
      "REPLAY-LOCK-REPORT-" + value.criteriaHash().substring(0, 16), value.correlationId(), "lock.audit_report_ready.v1", value.manifestHash()));
  }

  @Override
  Optional<LockModels.LockReplayResponse> findReplayIdempotency(UUID tenantId, String idempotencyKey, String replayHash) {
    Optional<LockModels.LockReplayResult> result = queryOne("select * from lock_replay_results where tenant_id = ? and idempotency_key = ?", (rs, rowNum) -> replayResult(rs), tenantId, idempotencyKey);
    if (result.isEmpty()) return Optional.empty();
    if (!result.get().inputHash().equals(replayHash)) throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Lock replay idempotency key was reused with a different payload");
    return Optional.of(replayResponse(result.get()));
  }

  @Override
  void saveReplayResult(LockModels.LockReplayResult result, LockModels.LockReplayResponse response, String replayHash, LockModels.LockEvent event, LockModels.AuditSnapshot audit) {
    jdbc.update("""
      insert into lock_replay_results (tenant_id, replay_id, lock_id, input_hash, config_graph_hash, event_sequence_hash,
        expected_result_hash, actual_result_hash, mismatch_class, evidence_hash, idempotency_key, correlation_id, replayed_at)
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """, result.tenantId(), result.replayId(), result.lockId(), replayHash, result.configGraphHash(), result.eventSequenceHash(),
      result.expectedResultHash(), result.actualResultHash(), result.mismatchClass().name(), result.evidenceHash(), result.idempotencyKey(), result.correlationId(), ts(result.replayedAt()));
    saveEvent(event);
    saveAudit(audit);
  }

  @Override
  Optional<LockModels.LockReplayResult> findReplayResult(UUID tenantId, String replayId) {
    return queryOne("select * from lock_replay_results where tenant_id = ? and replay_id = ?", (rs, rowNum) -> replayResult(rs), tenantId, replayId);
  }

  @Override
  Optional<LockModels.LockCancellationResponse> findCancellationIdempotency(UUID tenantId, String idempotencyKey, String cancellationHash) {
    Optional<LockModels.LockCancellationRecord> cancellation = queryOne("select * from lock_cancellations where tenant_id = ? and idempotency_key = ?", (rs, rowNum) -> cancellationRecord(rs), tenantId, idempotencyKey);
    if (cancellation.isEmpty()) return Optional.empty();
    if (!cancellation.get().evidenceHash().equals(cancellationHash)) throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Lock cancellation idempotency key was reused with a different payload");
    LockModels.RateLockRecord lock = find(tenantId, cancellation.get().lockId()).orElse(null);
    return Optional.of(new LockModels.LockCancellationResponse(tenantId, cancellation.get().cancellationId(), cancellation.get().lockId(),
      lock == null ? LockModels.RateLockStatus.ACTIVE : lock.status(), LockModels.RateLockStatus.CANCELLED, lock == null ? 0 : lock.version(),
      "Lock cancellation persisted with audit evidence", List.of(), "AUDIT-LOCK-CANCEL-" + cancellation.get().cancellationId(),
      "REPLAY-LOCK-CANCEL-" + cancellationHash.substring(0, Math.min(16, cancellationHash.length())), cancellation.get().correlationId(),
      "lock.cancelled.v1", cancellationHash));
  }

  @Override
  void saveCancellation(LockModels.RateLockRecord lockRecord, LockModels.LockCancellationRecord cancellation, LockModels.LockCancellationResponse response, String cancellationHash, LockModels.LockEvent event, LockModels.AuditSnapshot audit) {
    replace(lockRecord);
    jdbc.update("""
      insert into lock_cancellations (tenant_id, cancellation_id, lock_id, reason_code, cancelled_by, cancelled_at, policy_version_id,
        external_notify_required, evidence_hash, idempotency_key, correlation_id)
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """, cancellation.tenantId(), cancellation.cancellationId(), cancellation.lockId(), cancellation.reasonCode(), cancellation.cancelledBy(),
      ts(cancellation.cancelledAt()), cancellation.policyVersionId(), cancellation.externalNotifyRequired(), cancellationHash, cancellation.idempotencyKey(), cancellation.correlationId());
    saveEvent(event);
    saveAudit(audit);
  }

  @Override
  Optional<LockModels.LockCancellationRecord> findCancellation(UUID tenantId, String cancellationId) {
    return queryOne("select * from lock_cancellations where tenant_id = ? and cancellation_id = ?", (rs, rowNum) -> cancellationRecord(rs), tenantId, cancellationId);
  }

  @Override
  Optional<LockModels.LockEvidenceExportResponse> findEvidenceExportIdempotency(UUID tenantId, String idempotencyKey, String manifestHash) {
    Optional<LockModels.LockEvidenceExportRecord> export = queryOne("select * from lock_evidence_exports where tenant_id = ? and idempotency_key = ?", (rs, rowNum) -> evidenceExport(rs), tenantId, idempotencyKey);
    if (export.isEmpty()) return Optional.empty();
    if (!export.get().manifestHash().equals(manifestHash)) throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Evidence export idempotency key was reused with a different payload");
    return Optional.of(evidenceExportResponse(export.get()));
  }

  @Override
  void saveEvidenceExport(LockModels.LockEvidenceExportRecord export, LockModels.LockEvidenceExportResponse response, String manifestHash, LockModels.LockEvent event, LockModels.AuditSnapshot audit) {
    jdbc.update("""
      insert into lock_evidence_exports (tenant_id, export_id, report_id, actor_id, actor_refs, purpose_code, redacted_by_default,
        manifest_hash, event_ids, schema_versions, config_versions, snapshot_hashes, generated_file_hashes, idempotency_key, correlation_id, generated_at)
      values (?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, ?, ?)
      """, export.tenantId(), export.exportId(), export.reportId(), export.actorId(), writeJson(export.actorRefs()), export.purposeCode(),
      export.redactedByDefault(), manifestHash, writeJson(export.eventIds()), writeJson(export.schemaVersions()), writeJson(export.configVersions()),
      writeJson(export.snapshotHashes()), writeJson(export.generatedFileHashes()), export.idempotencyKey(), export.correlationId(), ts(export.generatedAt()));
    saveEvent(event);
    saveAudit(audit);
  }

  @Override
  Optional<LockModels.LockEvidenceExportRecord> findEvidenceExport(UUID tenantId, String exportId) {
    return queryOne("select * from lock_evidence_exports where tenant_id = ? and export_id = ?", (rs, rowNum) -> evidenceExport(rs), tenantId, exportId);
  }

  @Override
  List<LockModels.LockSyncAttempt> syncAttemptsForLock(UUID tenantId, String lockId) {
    return jdbc.query("select * from lock_sync_attempts where tenant_id = ? and lock_id = ? order by updated_at desc, attempt_id", (rs, rowNum) -> syncAttempt(rs), tenantId, lockId);
  }

  @Override
  void saveSyncAttempt(LockModels.LockSyncAttempt attempt, LockModels.LockEvent event, LockModels.AuditSnapshot audit) {
    jdbc.update("""
      insert into lock_sync_attempts (tenant_id, attempt_id, lock_id, event_id, target_id, status, payload_hash, retry_count,
        next_retry_at, ack_ref, correlation_id, policy_version, contract_version, updated_at)
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      on conflict (attempt_id) do update set status = excluded.status, retry_count = excluded.retry_count,
        next_retry_at = excluded.next_retry_at, ack_ref = excluded.ack_ref, updated_at = excluded.updated_at
      """, attempt.tenantId(), attempt.attemptId(), attempt.lockId(), attempt.eventId(), attempt.targetId(), attempt.status().name(),
      attempt.payloadHash(), attempt.retryCount(), ts(attempt.nextRetryAt()), attempt.ackRef(), attempt.correlationId(), attempt.policyVersion(),
      attempt.contractVersion(), ts(attempt.updatedAt()));
    saveEvent(event);
    saveAudit(audit);
  }

  @Override
  void saveSyncAcknowledgement(LockModels.RateLockRecord lockRecord, LockModels.LockSyncAttempt attempt, LockModels.LockSyncAcknowledgement acknowledgement, LockModels.LockReconciliationRecord reconciliation, LockModels.LockEvent event, LockModels.AuditSnapshot audit) {
    replace(lockRecord);
    saveSyncAttempt(attempt, null, null);
    jdbc.update("""
      insert into lock_sync_acknowledgements (tenant_id, ack_id, lock_id, event_id, target_id, ack_status, ack_ref, payload_hash, received_at, correlation_id)
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """, acknowledgement.tenantId(), acknowledgement.ackId(), acknowledgement.lockId(), acknowledgement.eventId(), acknowledgement.targetId(),
      acknowledgement.ackStatus().name(), acknowledgement.ackRef(), acknowledgement.payloadHash(), ts(acknowledgement.receivedAt()), acknowledgement.correlationId());
    if (reconciliation != null) {
      jdbc.update("""
        insert into lock_reconciliation_records (tenant_id, record_id, lock_id, target_system, drift_type, resolution, actor_id, reconciled_at, replay_ref, correlation_id)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, reconciliation.tenantId(), reconciliation.recordId(), reconciliation.lockId(), reconciliation.targetSystem(), reconciliation.driftType(),
        reconciliation.resolution(), reconciliation.actorId(), ts(reconciliation.reconciledAt()), reconciliation.replayRef(), reconciliation.correlationId());
    }
    saveEvent(event);
    saveAudit(audit);
  }

  @Override
  Optional<LockModels.LockSyncAcknowledgement> findSyncAcknowledgement(UUID tenantId, String ackId) {
    return queryOne("select * from lock_sync_acknowledgements where tenant_id = ? and ack_id = ?", (rs, rowNum) -> new LockModels.LockSyncAcknowledgement(
      (UUID) rs.getObject("tenant_id"), rs.getString("ack_id"), rs.getString("lock_id"), rs.getString("event_id"), rs.getString("target_id"),
      LockModels.LockSyncStatus.valueOf(rs.getString("ack_status")), rs.getString("ack_ref"), rs.getString("payload_hash"), instant(rs.getTimestamp("received_at")),
      rs.getString("correlation_id")), tenantId, ackId);
  }

  @Override
  Optional<LockModels.LockReconciliationRecord> findReconciliation(UUID tenantId, String recordId) {
    return queryOne("select * from lock_reconciliation_records where tenant_id = ? and record_id = ?", (rs, rowNum) -> new LockModels.LockReconciliationRecord(
      (UUID) rs.getObject("tenant_id"), rs.getString("record_id"), rs.getString("lock_id"), rs.getString("target_system"),
      rs.getString("drift_type"), rs.getString("resolution"), rs.getString("actor_id"), instant(rs.getTimestamp("reconciled_at")),
      rs.getString("replay_ref"), rs.getString("correlation_id")), tenantId, recordId);
  }

  @Override
  int lockCount() { return count("select count(*) from rate_locks"); }

  @Override
  int freshnessCheckCount() { return count("select count(*) from lock_freshness_checks"); }

  @Override
  int auditReportCount() { return count("select count(*) from lock_audit_reports"); }

  @Override
  int confirmationCount() { return count("select count(*) from lock_confirmations"); }

  @Override
  int expirationRunCount() { return count("select count(*) from lock_expiration_runs"); }

  @Override
  int extensionCount() { return count("select count(*) from lock_extensions"); }

  @Override
  int relockCount() { return count("select count(*) from lock_relocks"); }

  @Override
  int syncAttemptCount() { return count("select count(*) from lock_sync_attempts"); }

  @Override
  int syncAcknowledgementCount() { return count("select count(*) from lock_sync_acknowledgements"); }

  @Override
  int reconciliationCount() { return count("select count(*) from lock_reconciliation_records"); }

  @Override
  int replayResultCount() { return count("select count(*) from lock_replay_results"); }

  @Override
  int cancellationCount() { return count("select count(*) from lock_cancellations"); }

  @Override
  int evidenceExportCount() { return count("select count(*) from lock_evidence_exports"); }

  @Override
  List<LockModels.LockEvent> outboxEvents() {
    return jdbc.query("select * from lock_events order by occurred_at, event_id", (rs, rowNum) -> eventRecord(rs));
  }

  @Override
  List<LockModels.AuditSnapshot> auditSnapshots() {
    return jdbc.query("select * from lock_audit_snapshots order by created_at, audit_ref", (rs, rowNum) -> auditRecord(rs));
  }

  private Optional<LockModels.RateLockRecord> findByIdempotency(UUID tenantId, String idempotencyKey) {
    return queryOne("select * from rate_locks where tenant_id = ? and idempotency_key = ?", (rs, rowNum) -> lockRecord(rs), tenantId, idempotencyKey);
  }

  private void insertLock(LockModels.RateLockRecord record) {
    jdbc.update("""
      insert into rate_locks (tenant_id, lock_id, request_id, quote_id, loan_id, scenario_hash, status, version, created_at, updated_at,
        expires_at, idempotency_key, correlation_id, lock_policy_version_id, request_hash, audit_ref, replay_ref)
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """, record.tenantId(), record.lockId(), record.requestId(), record.quoteId(), record.loanId(), record.scenarioHash(),
      record.status().name(), record.version(), ts(record.createdAt()), ts(record.updatedAt()), ts(record.expiresAt()), record.idempotencyKey(),
      record.correlationId(), record.lockPolicyVersionId(), record.requestHash(), record.auditRef(), record.replayRef());
  }

  private void saveEvent(LockModels.LockEvent event) {
    if (event == null) return;
    jdbc.update("""
      insert into lock_events (tenant_id, event_id, event_type, event_key, payload_json, occurred_at)
      values (?, ?, ?, ?, cast(? as jsonb), ?)
      """, event.tenantId(), UUID.randomUUID(), event.eventType(), event.eventKey(), writeJson(eventPayload(event)), ts(event.occurredAt()));
  }

  private void saveAudit(LockModels.AuditSnapshot audit) {
    if (audit == null) return;
    jdbc.update("""
      insert into lock_audit_snapshots (tenant_id, audit_ref, lock_id, action, replay_hash, payload_json, created_at)
      values (?, ?, ?, ?, ?, cast(? as jsonb), ?)
      """, audit.tenantId(), audit.auditRef(), audit.lockId(), audit.action(), audit.replayHash(), writeJson(auditPayload(audit)), Timestamp.from(Instant.now()));
  }

  private Optional<LockModels.LockEvent> eventByIdempotency(UUID tenantId, String idempotencyKey, List<String> eventTypes) {
    return queryOne("select * from lock_events where tenant_id = ? and payload_json->>'idempotencyKey' = ? and event_type in (" + placeholders(eventTypes.size()) + ") order by occurred_at desc limit 1",
      (rs, rowNum) -> eventRecord(rs), argsWithStrings(tenantId, idempotencyKey, eventTypes));
  }

  private Optional<LockModels.AuditSnapshot> auditByRef(String auditRef) {
    return queryOne("select * from lock_audit_snapshots where audit_ref = ?", (rs, rowNum) -> auditRecord(rs), auditRef);
  }

  private LockModels.RateLockRecord lockRecord(ResultSet rs) throws SQLException {
    String auditRef = rs.getString("audit_ref");
    String eventType = auditRef != null && auditRef.contains("DECISION") ? "lock." + rs.getString("status").toLowerCase() + ".v1" : "lock.requested.v1";
    return new LockModels.RateLockRecord((UUID) rs.getObject("tenant_id"), rs.getString("lock_id"), rs.getString("request_id"),
      rs.getString("quote_id"), rs.getString("loan_id"), rs.getString("scenario_hash"), LockModels.RateLockStatus.valueOf(rs.getString("status")),
      rs.getInt("version"), instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")), instant(rs.getTimestamp("expires_at")),
      rs.getString("idempotency_key"), rs.getString("correlation_id"), rs.getString("lock_policy_version_id"), rs.getString("request_hash"),
      auditRef, rs.getString("replay_ref"), eventType);
  }

  private LockModels.FreshnessCheckRecord freshnessRecord(ResultSet rs) throws SQLException {
    return new LockModels.FreshnessCheckRecord((UUID) rs.getObject("tenant_id"), rs.getString("check_id"), rs.getString("quote_id"),
      rs.getString("scenario_hash"), rs.getString("policy_version"), LockModels.FreshnessDecisionType.valueOf(rs.getString("decision")),
      readStringList(rs.getString("reason_codes")), instant(rs.getTimestamp("evaluated_at")), instant(rs.getTimestamp("expires_at")),
      rs.getString("result_hash"), rs.getString("created_by"), rs.getString("correlation_id"));
  }

  private LockModels.LockConfirmationRecord confirmationRecord(ResultSet rs) throws SQLException {
    return new LockModels.LockConfirmationRecord((UUID) rs.getObject("tenant_id"), rs.getString("confirmation_id"), rs.getString("lock_id"),
      LockModels.LockConfirmationType.valueOf(rs.getString("confirmation_type")), rs.getString("lock_number"), rs.getString("investor_id"),
      rs.getString("investor_confirmation_ref"), LockModels.RateLockStatus.valueOf(rs.getString("status")), rs.getInt("lock_version"),
      instant(rs.getTimestamp("confirmed_at")), instant(rs.getTimestamp("expires_at")), 0, null, "",
      emptyBreakdown(), rs.getString("confirmed_terms_hash"), rs.getString("idempotency_key"), rs.getString("correlation_id"), rs.getString("replay_ref"));
  }

  private LockModels.LockConfirmationResponse confirmationResponse(LockModels.RateLockRecord lock, LockModels.LockConfirmationRecord confirmation, String confirmationHash) {
    return new LockModels.LockConfirmationResponse(confirmation.tenantId(), confirmation.lockId(), confirmation.confirmationId(), lock.status(),
      confirmation.status(), lock.version(), confirmation.lockNumber(), confirmation.investorConfirmationRef(),
      "Lock confirmation persisted with investor/reference uniqueness checks", List.of(), "AUDIT-CONFIRMATION-" + confirmation.confirmationId(),
      confirmation.replayRef(), confirmation.correlationId(), "lock.confirmed.v1", confirmation.expirationBusinessDays(), lock.createdAt(),
      confirmation.expiresAt(), new LockModels.CalendarConfigSummary("", "", 0), confirmation.expirationBreakdown(), confirmation.calendarConfigHash(), confirmationHash);
  }

  private LockModels.LockExtensionRecord extensionRecord(ResultSet rs) throws SQLException {
    return new LockModels.LockExtensionRecord((UUID) rs.getObject("tenant_id"), rs.getString("extension_id"), rs.getString("lock_id"),
      LockModels.LockExtensionStatus.valueOf(rs.getString("status")), rs.getInt("lock_version"), rs.getInt("requested_days"),
      instant(rs.getTimestamp("previous_expires_at")), instant(rs.getTimestamp("requested_expires_at")), rs.getString("reason_code"),
      rs.getString("requested_by"), rs.getString("approved_by"), instant(rs.getTimestamp("requested_at")), instant(rs.getTimestamp("decided_at")),
      instant(rs.getTimestamp("confirmed_at")), rs.getString("policy_version_id"), rs.getString("cost_snapshot_hash"), rs.getString("idempotency_key"),
      rs.getString("correlation_id"), rs.getString("replay_ref"));
  }

  private LockModels.LockExtensionResponse extensionResponse(LockModels.RateLockRecord lock, LockModels.LockExtensionRecord extension, String resultHash) {
    return new LockModels.LockExtensionResponse(extension.tenantId(), extension.lockId(), extension.extensionId(), extension.status(), lock.status(), lock.version(),
      lock.expiresAt(), extension.requestedDays(), extension.requestedExpiresAt(), costSnapshot(extension.tenantId(), extension.extensionId()), emptyBreakdown(), "",
      "Lock extension persisted with open-extension uniqueness checks", List.of(), "AUDIT-EXTENSION-" + extension.extensionId(), extension.replayRef(),
      extension.correlationId(), extension.status() == LockModels.LockExtensionStatus.REQUESTED ? "lock.extension_requested.v1" : "lock.extension_decisioned.v1", resultHash);
  }

  private LockModels.RelockRecord relockRecord(ResultSet rs) throws SQLException {
    return new LockModels.RelockRecord((UUID) rs.getObject("tenant_id"), rs.getString("relock_id"), rs.getString("source_lock_id"),
      rs.getString("replacement_lock_id"), rs.getString("current_quote_id"), LockModels.RelockStatus.valueOf(rs.getString("status")),
      rs.getInt("source_lock_version"), rs.getString("requested_by"), rs.getString("approved_by"), instant(rs.getTimestamp("requested_at")),
      instant(rs.getTimestamp("decided_at")), instant(rs.getTimestamp("confirmed_at")), rs.getString("reason_code"), rs.getString("policy_version_id"),
      false, rs.getString("comparison_hash"), rs.getString("idempotency_key"), rs.getString("correlation_id"), rs.getString("replay_ref"));
  }

  private LockModels.RelockResponse relockResponse(LockModels.RateLockRecord source, LockModels.RateLockRecord replacement, LockModels.RelockRecord relock) {
    return new LockModels.RelockResponse(relock.tenantId(), relock.relockId(), relock.sourceLockId(), relock.replacementLockId(), relock.status(),
      source.status(), replacement.status(), replacement.version(), "Relock state persisted with open-source-lock uniqueness checks", List.of(),
      "AUDIT-RELOCK-" + relock.relockId(), relock.replayRef(), relock.correlationId(),
      relock.status() == LockModels.RelockStatus.REQUESTED ? "lock.relock_requested.v1" : "lock.relock_decisioned.v1", relock.comparisonHash());
  }

  private LockModels.LockExpirationSchedule expirationSchedule(ResultSet rs) throws SQLException {
    return new LockModels.LockExpirationSchedule((UUID) rs.getObject("tenant_id"), rs.getString("lock_id"), instant(rs.getTimestamp("expires_at")),
      0, null, "", emptyBreakdown(), instant(rs.getTimestamp("next_warning_at")), rs.getString("policy_version"), instant(rs.getTimestamp("last_evaluated_at")));
  }

  private LockModels.LockSyncAttempt syncAttempt(ResultSet rs) throws SQLException {
    return new LockModels.LockSyncAttempt((UUID) rs.getObject("tenant_id"), rs.getString("attempt_id"), rs.getString("lock_id"), rs.getString("event_id"),
      rs.getString("target_id"), LockModels.LockSyncStatus.valueOf(rs.getString("status")), rs.getString("payload_hash"), rs.getInt("retry_count"),
      instant(rs.getTimestamp("next_retry_at")), rs.getString("ack_ref"), rs.getString("correlation_id"), rs.getString("policy_version"),
      rs.getString("contract_version"), instant(rs.getTimestamp("updated_at")));
  }

  private LockModels.LockReplayResult replayResult(ResultSet rs) throws SQLException {
    return new LockModels.LockReplayResult((UUID) rs.getObject("tenant_id"), rs.getString("replay_id"), rs.getString("lock_id"), rs.getString("input_hash"),
      rs.getString("config_graph_hash"), rs.getString("event_sequence_hash"), rs.getString("expected_result_hash"), rs.getString("actual_result_hash"),
      LockModels.LockReplayMismatchClass.valueOf(rs.getString("mismatch_class")), rs.getString("evidence_hash"), rs.getString("idempotency_key"),
      rs.getString("correlation_id"), instant(rs.getTimestamp("replayed_at")));
  }

  private LockModels.LockReplayResponse replayResponse(LockModels.LockReplayResult result) {
    return new LockModels.LockReplayResponse(result.tenantId(), result.replayId(), result.lockId(), result.mismatchClass(), result.evidenceHash(),
      "Lock replay result persisted with captured input/config/event hashes", List.of(), "AUDIT-LOCK-REPLAY-" + result.replayId(),
      "REPLAY-LOCK-REPLAY-" + result.inputHash().substring(0, Math.min(16, result.inputHash().length())), result.correlationId(), "lock.replay_completed.v1");
  }

  private LockModels.LockCancellationRecord cancellationRecord(ResultSet rs) throws SQLException {
    return new LockModels.LockCancellationRecord((UUID) rs.getObject("tenant_id"), rs.getString("cancellation_id"), rs.getString("lock_id"),
      rs.getString("reason_code"), rs.getString("cancelled_by"), instant(rs.getTimestamp("cancelled_at")), rs.getString("policy_version_id"),
      rs.getBoolean("external_notify_required"), rs.getString("evidence_hash"), rs.getString("idempotency_key"), rs.getString("correlation_id"));
  }

  private LockModels.LockEvidenceExportRecord evidenceExport(ResultSet rs) throws SQLException {
    return new LockModels.LockEvidenceExportRecord((UUID) rs.getObject("tenant_id"), rs.getString("export_id"), rs.getString("report_id"),
      rs.getString("actor_id"), readStringMap(rs.getString("actor_refs")), rs.getString("purpose_code"), rs.getBoolean("redacted_by_default"),
      rs.getString("manifest_hash"), readStringList(rs.getString("event_ids")), readStringMap(rs.getString("schema_versions")),
      readStringMap(rs.getString("config_versions")), readStringMap(rs.getString("snapshot_hashes")), readStringMap(rs.getString("generated_file_hashes")),
      rs.getString("idempotency_key"), rs.getString("correlation_id"), instant(rs.getTimestamp("generated_at")));
  }

  private LockModels.LockEvidenceExportResponse evidenceExportResponse(LockModels.LockEvidenceExportRecord export) {
    return new LockModels.LockEvidenceExportResponse(export.tenantId(), export.exportId(), export.reportId(), "READY", export.manifestHash(), export.eventIds(),
      export.schemaVersions(), export.configVersions(), export.snapshotHashes(), export.actorRefs(), export.generatedFileHashes(), export.redactedByDefault(),
      export.actorId(), List.of(), "AUDIT-EVIDENCE-EXPORT-" + export.exportId(),
      "REPLAY-EVIDENCE-EXPORT-" + export.manifestHash().substring(0, Math.min(16, export.manifestHash().length())), export.correlationId(), "lock.evidence_export_ready.v1");
  }

  private void upsertExtension(LockModels.LockExtensionRecord extension, LockModels.ExtensionCostSnapshot costSnapshot, String resultHash) {
    jdbc.update("""
      insert into lock_extensions (tenant_id, extension_id, lock_id, status, lock_version, requested_days, previous_expires_at,
        requested_expires_at, reason_code, requested_by, approved_by, requested_at, decided_at, confirmed_at, policy_version_id,
        cost_snapshot_hash, idempotency_key, correlation_id, replay_ref)
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      on conflict (extension_id) do update set status = excluded.status, lock_version = excluded.lock_version,
        approved_by = excluded.approved_by, decided_at = excluded.decided_at, confirmed_at = excluded.confirmed_at,
        cost_snapshot_hash = excluded.cost_snapshot_hash, idempotency_key = excluded.idempotency_key, replay_ref = excluded.replay_ref
      """, extension.tenantId(), extension.extensionId(), extension.lockId(), extension.status().name(), extension.lockVersion(), extension.requestedDays(),
      ts(extension.previousExpiresAt()), ts(extension.requestedExpiresAt()), extension.reasonCode(), extension.requestedBy(), extension.approvedBy(),
      ts(extension.requestedAt()), ts(extension.decidedAt()), ts(extension.confirmedAt()), extension.policyVersionId(), resultHash,
      extension.idempotencyKey(), extension.correlationId(), extension.replayRef());
    LockModels.ExtensionCostSnapshot cost = costSnapshot == null ? emptyCostSnapshot(extension.policyVersionId(), extension.reasonCode()) : costSnapshot;
    jdbc.update("""
      insert into lock_extension_cost_snapshots (tenant_id, extension_id, price_adjustment_ref, fee_amount_ref, payer_type, rounding_mode,
        reason_code, policy_version_id, cost_snapshot_hash, created_at)
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      on conflict (tenant_id, extension_id) do update set price_adjustment_ref = excluded.price_adjustment_ref,
        fee_amount_ref = excluded.fee_amount_ref, payer_type = excluded.payer_type, rounding_mode = excluded.rounding_mode,
        reason_code = excluded.reason_code, policy_version_id = excluded.policy_version_id, cost_snapshot_hash = excluded.cost_snapshot_hash
      """, extension.tenantId(), extension.extensionId(), cost.priceAdjustment(), cost.feeAmount(), cost.payerType(), cost.roundingMode(),
      cost.reasonCode(), cost.policyVersionId(), resultHash, ts(extension.requestedAt()));
  }

  private void upsertRelock(LockModels.RelockRecord relock, String resultHash) {
    jdbc.update("""
      insert into lock_relocks (tenant_id, relock_id, source_lock_id, replacement_lock_id, current_quote_id, status, source_lock_version,
        requested_by, approved_by, requested_at, decided_at, confirmed_at, reason_code, policy_version_id, comparison_hash,
        idempotency_key, correlation_id, replay_ref)
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      on conflict (relock_id) do update set status = excluded.status, source_lock_version = excluded.source_lock_version,
        approved_by = excluded.approved_by, decided_at = excluded.decided_at, confirmed_at = excluded.confirmed_at,
        comparison_hash = excluded.comparison_hash, idempotency_key = excluded.idempotency_key, replay_ref = excluded.replay_ref
      """, relock.tenantId(), relock.relockId(), relock.sourceLockId(), relock.replacementLockId(), relock.currentQuoteId(), relock.status().name(),
      relock.sourceLockVersion(), relock.requestedBy(), relock.approvedBy(), ts(relock.requestedAt()), ts(relock.decidedAt()), ts(relock.confirmedAt()),
      relock.reasonCode(), relock.policyVersionId(), resultHash, relock.idempotencyKey(), relock.correlationId(), relock.replayRef());
  }

  private void upsertExpirationSchedule(LockModels.LockExpirationSchedule schedule) {
    jdbc.update("""
      insert into lock_expiration_schedules (tenant_id, lock_id, expires_at, next_warning_at, policy_version, last_evaluated_at)
      values (?, ?, ?, ?, ?, ?)
      on conflict (tenant_id, lock_id) do update set expires_at = excluded.expires_at, next_warning_at = excluded.next_warning_at,
        policy_version = excluded.policy_version, last_evaluated_at = excluded.last_evaluated_at
      """, schedule.tenantId(), schedule.lockId(), ts(schedule.expiresAt()), ts(schedule.nextWarningAt()), schedule.policyVersionId(), ts(schedule.lastEvaluatedAt()));
  }

  private LockModels.ExtensionCostSnapshot costSnapshot(UUID tenantId, String extensionId) {
    return queryOne("select * from lock_extension_cost_snapshots where tenant_id = ? and extension_id = ?", (rs, rowNum) -> new LockModels.ExtensionCostSnapshot(
      rs.getString("price_adjustment_ref"), rs.getString("fee_amount_ref"), rs.getString("payer_type"), rs.getString("rounding_mode"),
      rs.getString("reason_code"), rs.getString("policy_version_id")), tenantId, extensionId).orElse(emptyCostSnapshot("", ""));
  }

  private static LockModels.ExtensionCostSnapshot emptyCostSnapshot(String policyVersionId, String reasonCode) {
    return new LockModels.ExtensionCostSnapshot("", "", "", "", safe(reasonCode), safe(policyVersionId));
  }

  private static BusinessDayCalculator.ExpirationBreakdown emptyBreakdown() {
    return new BusinessDayCalculator.ExpirationBreakdown(0, 0, List.of(), 0);
  }

  private LockModels.LockEvent eventRecord(ResultSet rs) throws SQLException {
    Map<String, String> payload = readStringMap(rs.getString("payload_json"));
    return new LockModels.LockEvent(rs.getString("event_type"), payload.getOrDefault("eventVersion", "1"), rs.getString("event_key"),
      (UUID) rs.getObject("tenant_id"), payload.getOrDefault("lockId", payload.getOrDefault("aggregateId", "")),
      payload.getOrDefault("actorId", ""), payload.getOrDefault("correlationId", ""), payload.getOrDefault("causationId", ""),
      payload.getOrDefault("idempotencyKey", ""), instant(rs.getTimestamp("occurred_at")), payload);
  }

  private LockModels.AuditSnapshot auditRecord(ResultSet rs) throws SQLException {
    Map<String, String> payload = readStringMap(rs.getString("payload_json"));
    return new LockModels.AuditSnapshot(rs.getString("audit_ref"), (UUID) rs.getObject("tenant_id"), rs.getString("lock_id"),
      rs.getString("action"), payload.getOrDefault("actorId", ""), payload.get("beforeState"), payload.get("afterState"),
      payload.get("lockPolicyVersionId"), payload.get("complianceEvidenceRef"), payload.getOrDefault("correlationId", ""), rs.getString("replay_hash"));
  }

  private Map<String, String> auditPayload(LockModels.AuditSnapshot audit) {
    return Map.of(
      "actorId", safe(audit.actorId()),
      "beforeState", safe(audit.beforeState()),
      "afterState", safe(audit.afterState()),
      "lockPolicyVersionId", safe(audit.lockPolicyVersionId()),
      "complianceEvidenceRef", safe(audit.complianceEvidenceRef()),
      "correlationId", safe(audit.correlationId())
    );
  }

  private Map<String, String> eventPayload(LockModels.LockEvent event) {
    java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>(event.payload());
    payload.put("eventVersion", safe(event.eventVersion()));
    payload.put("lockId", safe(event.lockId()));
    payload.put("actorId", safe(event.actorId()));
    payload.put("correlationId", safe(event.correlationId()));
    payload.put("causationId", safe(event.causationId()));
    payload.put("idempotencyKey", safe(event.idempotencyKey()));
    return payload;
  }

  private String writeJson(Object value) {
    try { return mapper.writeValueAsString(value); } catch (Exception ex) { throw new LockServiceException("PERSISTENCE_SERIALIZATION_FAILED", ex.getMessage()); }
  }

  private Map<String, String> readStringMap(String json) {
    try { return mapper.readValue(json, new TypeReference<Map<String, String>>() {}); } catch (Exception ex) { return Map.of(); }
  }

  private List<String> readStringList(String json) {
    try { return mapper.readValue(json, new TypeReference<List<String>>() {}); } catch (Exception ex) { return List.of(); }
  }

  private int count(String sql, Object... args) {
    Integer count = jdbc.queryForObject(sql, Integer.class, args);
    return count == null ? 0 : count;
  }

  private <T> Optional<T> queryOne(String sql, org.springframework.jdbc.core.RowMapper<T> mapper, Object... args) {
    try { return Optional.ofNullable(jdbc.queryForObject(sql, mapper, args)); } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
  }

  private static Object[] argsWithStatuses(UUID tenantId, String quoteId) {
    Object[] args = new Object[2 + ACTIVE_STATUSES.size()];
    args[0] = tenantId;
    args[1] = quoteId;
    for (int i = 0; i < ACTIVE_STATUSES.size(); i++) args[i + 2] = ACTIVE_STATUSES.get(i);
    return args;
  }

  private static Object[] argsWithStrings(UUID tenantId, String idempotencyKey, List<String> values) {
    Object[] args = new Object[2 + values.size()];
    args[0] = tenantId;
    args[1] = idempotencyKey;
    for (int i = 0; i < values.size(); i++) args[i + 2] = values.get(i);
    return args;
  }

  private static String placeholders(int count) {
    return String.join(",", java.util.Collections.nCopies(count, "?"));
  }

  private static Timestamp ts(Instant instant) { return instant == null ? null : Timestamp.from(instant); }

  private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }

  private static String safe(String value) { return value == null ? "" : value; }
}
