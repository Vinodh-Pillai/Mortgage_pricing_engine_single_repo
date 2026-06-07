package com.wcpe.auditreplay.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.CorrelationContext;
import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.domain.LegalHold;
import com.wcpe.auditreplay.domain.LegalHoldItem;
import com.wcpe.auditreplay.domain.LegalHoldStatus;
import com.wcpe.auditreplay.domain.RetentionPolicy;
import com.wcpe.auditreplay.domain.RetentionPolicyStatus;
import com.wcpe.auditreplay.domain.RetentionPurgeRun;
import com.wcpe.auditreplay.domain.RetentionPurgeRunStatus;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import com.wcpe.auditreplay.repository.LegalHoldItemRepository;
import com.wcpe.auditreplay.repository.LegalHoldRepository;
import com.wcpe.auditreplay.repository.RetentionPolicyRepository;
import com.wcpe.auditreplay.repository.RetentionPurgeRunRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetentionLegalHoldService {

    public static final String RETENTION_POLICY_PUBLISHED_EVENT = "RetentionPolicyPublished.v1";
    public static final String LEGAL_HOLD_APPLIED_EVENT = "LegalHoldApplied.v1";
    public static final String LEGAL_HOLD_RELEASED_EVENT = "LegalHoldReleased.v1";
    public static final String RETENTION_PURGE_COMPLETED_EVENT = "RetentionPurgeCompleted.v1";
    static final String RETENTION_LEGAL_HOLD_SCHEMA_REF = "retention-legal-hold-v1";

    private final RetentionPolicyRepository retentionPolicyRepository;
    private final LegalHoldRepository legalHoldRepository;
    private final LegalHoldItemRepository legalHoldItemRepository;
    private final RetentionPurgeRunRepository purgeRunRepository;
    private final AuditRecordRepository auditRecordRepository;
    private final OutboxRecorder outboxRecorder;
    private final ObjectMapper objectMapper;

    public RetentionLegalHoldService(
            RetentionPolicyRepository retentionPolicyRepository,
            LegalHoldRepository legalHoldRepository,
            LegalHoldItemRepository legalHoldItemRepository,
            RetentionPurgeRunRepository purgeRunRepository,
            AuditRecordRepository auditRecordRepository,
            OutboxRecorder outboxRecorder,
            ObjectMapper objectMapper) {
        this.retentionPolicyRepository = retentionPolicyRepository;
        this.legalHoldRepository = legalHoldRepository;
        this.legalHoldItemRepository = legalHoldItemRepository;
        this.purgeRunRepository = purgeRunRepository;
        this.auditRecordRepository = auditRecordRepository;
        this.outboxRecorder = outboxRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RetentionPolicy publishPolicy(RetentionPolicyCommand command) {
        validatePolicy(command);
        return retentionPolicyRepository.findByTenantIdAndIdempotencyKey(command.tenantId(), command.idempotencyKey())
                .orElseGet(() -> createPolicy(command));
    }

    @Transactional
    public LegalHold applyHold(LegalHoldCommand command) {
        validateHold(command);
        return legalHoldRepository.findByTenantIdAndIdempotencyKey(command.tenantId(), command.idempotencyKey())
                .orElseGet(() -> createHold(command));
    }

    @Transactional
    public LegalHold releaseHold(UUID tenantId, UUID holdId, LegalHoldReleaseCommand command) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(holdId, "holdId is required");
        validateRelease(command);
        LegalHold hold = legalHoldRepository.findByTenantIdAndHoldId(tenantId, holdId)
                .orElseThrow(() -> new RetentionLegalHoldNotFoundException("Legal hold not found"));
        if (hold.getStatus() == LegalHoldStatus.RELEASED) {
            return hold;
        }
        hold.release(command.releasedBy(), command.releaseApprovedBy());
        legalHoldItemRepository.findAllByTenantIdAndHoldId(tenantId, holdId).forEach(LegalHoldItem::release);
        outboxRecorder.record(toHoldOutboxCommand(hold, LEGAL_HOLD_RELEASED_EVENT, command.releasedBy(), command.idempotencyKey(), "released"));
        return hold;
    }

    @Transactional
    public RetentionPurgeRun runPurge(RetentionPurgeCommand command) {
        validatePurge(command);
        return purgeRunRepository.findByTenantIdAndIdempotencyKey(command.tenantId(), command.idempotencyKey())
                .orElseGet(() -> createPurgeRun(command));
    }

    @Transactional(readOnly = true)
    public RetentionPurgeRun getPurgeRun(UUID tenantId, UUID runId) {
        return purgeRunRepository.findByTenantIdAndRunId(tenantId, runId)
                .orElseThrow(() -> new RetentionLegalHoldNotFoundException("Retention purge run not found"));
    }

    public JsonNode report(RetentionPurgeRun run) {
        try {
            return objectMapper.readTree(run.getReportJson());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Retention purge report JSON cannot be parsed", ex);
        }
    }

    private RetentionPolicy createPolicy(RetentionPolicyCommand command) {
        List<RetentionPolicy> overlaps = retentionPolicyRepository.findAllByTenantIdAndEvidenceTypeAndJurisdictionAndStatus(
                command.tenantId(), command.evidenceType(), command.jurisdiction(), RetentionPolicyStatus.PUBLISHED).stream()
                .filter(policy -> policy.overlaps(command.effectiveFrom(), command.effectiveTo()))
                .toList();
        if (!overlaps.isEmpty()) {
            throw new PolicyNotSatisfiedException("Published retention policy overlaps an active version for this tenant/evidence/jurisdiction");
        }
        UUID policyId = command.policyId() == null ? deterministicId(command.tenantId(), command.idempotencyKey()) : command.policyId();
        RetentionPolicy policy = RetentionPolicy.publish(
                policyId,
                command.tenantId(),
                command.evidenceType(),
                command.jurisdiction(),
                command.productFilter(),
                command.channelFilter(),
                command.retentionDays(),
                command.effectiveFrom(),
                command.effectiveTo(),
                command.createdBy(),
                command.approvedBy(),
                command.correlationId(),
                command.idempotencyKey());
        RetentionPolicy saved = retentionPolicyRepository.save(policy);
        outboxRecorder.record(toPolicyOutboxCommand(saved));
        return saved;
    }

    private LegalHold createHold(LegalHoldCommand command) {
        byte[] scope = writeJson(command.scope());
        UUID holdId = command.holdId() == null ? deterministicId(command.tenantId(), command.idempotencyKey()) : command.holdId();
        LegalHold hold = LegalHold.apply(
                holdId,
                command.tenantId(),
                command.caseId(),
                scope,
                command.reason(),
                command.appliedBy(),
                command.approvedBy(),
                command.correlationId(),
                command.idempotencyKey());
        LegalHold saved = legalHoldRepository.save(hold);
        resolveAuditRecordIds(command.scope()).forEach(auditRecordId -> legalHoldItemRepository.save(
                LegalHoldItem.active(command.tenantId(), saved.getHoldId(), "AUDIT_RECORD", auditRecordId.toString(), auditRecordId, null, null)));
        resolveExportIds(command.scope()).forEach(exportId -> legalHoldItemRepository.save(
                LegalHoldItem.active(command.tenantId(), saved.getHoldId(), "EVIDENCE_EXPORT", exportId.toString(), null, exportId, null)));
        resolveReplayRunIds(command.scope()).forEach(runId -> legalHoldItemRepository.save(
                LegalHoldItem.active(command.tenantId(), saved.getHoldId(), "REPLAY_RUN", runId.toString(), null, null, runId)));
        outboxRecorder.record(toHoldOutboxCommand(saved, LEGAL_HOLD_APPLIED_EVENT, saved.getAppliedBy(), saved.getIdempotencyKey(), "applied"));
        return saved;
    }

    private RetentionPurgeRun createPurgeRun(RetentionPurgeCommand command) {
        List<AuditRecord> candidates = auditRecordRepository.findAllByTenantIdAndRetentionUntilBeforeOrderByRetentionUntilAscCreatedAtAscIdAsc(
                command.tenantId(), command.cutoff());
        List<AuditRecord> held = candidates.stream()
                .filter(record -> record.isLegalHold()
                        || legalHoldItemRepository.existsByTenantIdAndAuditRecordIdAndStatus(command.tenantId(), record.getId(), LegalHoldStatus.ACTIVE))
                .toList();
        int purgeable = candidates.size() - held.size();
        int purgedCount = command.dryRun() ? 0 : purgeable;
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", "retention-purge-report-v1");
        report.put("tenantId", command.tenantId().toString());
        report.put("cutoff", command.cutoff().toString());
        report.put("dryRun", command.dryRun());
        report.put("candidateAuditRecordIds", candidates.stream().map(AuditRecord::getId).map(UUID::toString).sorted().toList());
        report.put("skippedHeldAuditRecordIds", held.stream().map(AuditRecord::getId).map(UUID::toString).sorted().toList());
        report.put("purgeMode", command.dryRun() ? "REPORT_ONLY" : "TOMBSTONE_ONLY_NO_PHYSICAL_DELETE");
        byte[] reportJson = writeJson(report);
        RetentionPurgeRun run = RetentionPurgeRun.create(
                deterministicId(command.tenantId(), command.idempotencyKey()),
                command.tenantId(),
                command.cutoff(),
                candidates.size(),
                purgedCount,
                held.size(),
                command.dryRun() ? RetentionPurgeRunStatus.DRY_RUN : RetentionPurgeRunStatus.COMPLETED,
                reportJson,
                AuditRecorder.sha256Hex(reportJson),
                command.requestedBy(),
                command.correlationId(),
                command.idempotencyKey());
        RetentionPurgeRun saved = purgeRunRepository.save(run);
        outboxRecorder.record(toPurgeOutboxCommand(saved));
        return saved;
    }

    private OutboxRecordCommand toPolicyOutboxCommand(RetentionPolicy policy) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", policy.getPolicyId().toString());
        payload.put("tenantId", policy.getTenantId().toString());
        payload.put("status", policy.getStatus().name());
        payload.put("version", policy.getVersion());
        payload.put("summary", "Retention policy published for " + policy.getEvidenceType() + "/" + policy.getJurisdiction());
        payload.put("sourceRefs", List.of("retention-policy:" + policy.getPolicyId()));
        payload.put("retentionDays", policy.getRetentionDays());
        payload.put("effectiveFrom", policy.getEffectiveFrom().toString());
        payload.put("effectiveTo", policy.getEffectiveTo() == null ? null : policy.getEffectiveTo().toString());
        return new OutboxRecordCommand(policy.getTenantId(), "retention-policy", policy.getPolicyId().toString(), policy.getVersion().longValue(),
                RETENTION_POLICY_PUBLISHED_EVENT, 1, policy.getTenantId() + ":" + policy.getPolicyId() + ":published",
                policy.getTenantId().toString(), payload, policy.getCorrelationId(), policy.getPolicyId(), policy.getApprovedBy(),
                policy.getIdempotencyKey() + ":published", RETENTION_LEGAL_HOLD_SCHEMA_REF);
    }

    private OutboxRecordCommand toHoldOutboxCommand(LegalHold hold, String eventType, String actorId, String idempotencyKey, String phase) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", hold.getHoldId().toString());
        payload.put("tenantId", hold.getTenantId().toString());
        payload.put("status", hold.getStatus().name());
        payload.put("version", 1);
        payload.put("summary", "Legal hold " + phase + " for case " + hold.getCaseId());
        payload.put("sourceRefs", List.of("legal-hold:" + hold.getHoldId()));
        payload.put("caseId", hold.getCaseId());
        payload.put("impactCount", legalHoldItemRepository.countByTenantIdAndHoldIdAndStatus(hold.getTenantId(), hold.getHoldId(), LegalHoldStatus.ACTIVE));
        return new OutboxRecordCommand(hold.getTenantId(), "legal-hold", hold.getHoldId().toString(), 1L,
                eventType, 1, hold.getTenantId() + ":" + hold.getHoldId() + ":" + phase, hold.getTenantId().toString(),
                payload, hold.getCorrelationId(), hold.getHoldId(), actorId, idempotencyKey + ":" + phase, RETENTION_LEGAL_HOLD_SCHEMA_REF);
    }

    private OutboxRecordCommand toPurgeOutboxCommand(RetentionPurgeRun run) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", run.getRunId().toString());
        payload.put("tenantId", run.getTenantId().toString());
        payload.put("status", run.getStatus().name());
        payload.put("version", 1);
        payload.put("summary", "Retention purge " + run.getStatus().name() + " candidates=" + run.getCandidateCount() + " skippedHeld=" + run.getSkippedHeldCount());
        payload.put("sourceRefs", List.of("retention-purge-run:" + run.getRunId()));
        payload.put("candidateCount", run.getCandidateCount());
        payload.put("purgedCount", run.getPurgedCount());
        payload.put("skippedHeldCount", run.getSkippedHeldCount());
        payload.put("reportHash", run.getReportHash());
        return new OutboxRecordCommand(run.getTenantId(), "retention-purge-run", run.getRunId().toString(), 1L,
                RETENTION_PURGE_COMPLETED_EVENT, 1, run.getTenantId() + ":" + run.getRunId() + ":completed",
                run.getTenantId().toString(), payload, run.getCorrelationId(), run.getRunId(), run.getRequestedBy(),
                run.getIdempotencyKey() + ":completed", RETENTION_LEGAL_HOLD_SCHEMA_REF);
    }

    private void validatePolicy(RetentionPolicyCommand command) {
        Objects.requireNonNull(command, "command is required");
        Objects.requireNonNull(command.tenantId(), "tenantId is required");
        requireText(command.evidenceType(), "evidenceType is required");
        requireText(command.jurisdiction(), "jurisdiction is required");
        if (command.retentionDays() == null || command.retentionDays() <= 0) {
            throw new PolicyNotSatisfiedException("tenant retentionDays policy is required");
        }
        Objects.requireNonNull(command.effectiveFrom(), "effectiveFrom is required");
        requireText(command.createdBy(), "createdBy is required");
        requireText(command.approvedBy(), "approvedBy is required");
        requireContext(command.correlationId());
        requireText(command.idempotencyKey(), "Idempotency-Key is required");
    }

    private void validateHold(LegalHoldCommand command) {
        Objects.requireNonNull(command, "command is required");
        Objects.requireNonNull(command.tenantId(), "tenantId is required");
        requireText(command.caseId(), "caseId is required");
        requireText(command.reason(), "reason is required");
        requireText(command.appliedBy(), "appliedBy is required");
        requireText(command.approvedBy(), "approvedBy is required");
        if (command.scope() == null || command.scope().isEmpty()) {
            throw new PolicyNotSatisfiedException("legal hold scope is required");
        }
        requireContext(command.correlationId());
        requireText(command.idempotencyKey(), "Idempotency-Key is required");
    }

    private void validateRelease(LegalHoldReleaseCommand command) {
        Objects.requireNonNull(command, "command is required");
        requireText(command.releasedBy(), "releasedBy is required");
        requireText(command.releaseApprovedBy(), "releaseApprovedBy is required");
        requireText(command.idempotencyKey(), "Idempotency-Key is required");
    }

    private void validatePurge(RetentionPurgeCommand command) {
        Objects.requireNonNull(command, "command is required");
        Objects.requireNonNull(command.tenantId(), "tenantId is required");
        Objects.requireNonNull(command.cutoff(), "cutoff is required");
        requireText(command.requestedBy(), "requestedBy is required");
        requireContext(command.correlationId());
        requireText(command.idempotencyKey(), "Idempotency-Key is required");
    }

    private void requireContext(UUID correlationId) {
        Objects.requireNonNull(correlationId, "correlationId is required");
        CorrelationContext.Data context = CorrelationContext.get();
        if (context == null || context.correlationId() == null || !correlationId.equals(context.correlationId().getValue())) {
            throw new IllegalArgumentException("correlationId must match current CorrelationContext");
        }
    }

    private List<UUID> resolveAuditRecordIds(Map<String, Object> scope) {
        return resolveUuidList(scope, "auditRecordIds");
    }

    private List<UUID> resolveExportIds(Map<String, Object> scope) {
        return resolveUuidList(scope, "exportIds");
    }

    private List<UUID> resolveReplayRunIds(Map<String, Object> scope) {
        return resolveUuidList(scope, "replayRunIds");
    }

    private List<UUID> resolveUuidList(Map<String, Object> scope, String field) {
        Object value = scope.get(field);
        if (value == null) {
            return List.of();
        }
        return objectMapper.convertValue(value, new TypeReference<List<String>>() {}).stream().map(UUID::fromString).distinct().sorted().toList();
    }

    private byte[] writeJson(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Retention legal hold JSON cannot be serialized", ex);
        }
    }

    private static UUID deterministicId(UUID tenantId, String idempotencyKey) {
        return UUID.nameUUIDFromBytes((tenantId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    public record RetentionPolicyCommand(
            UUID tenantId,
            UUID policyId,
            String evidenceType,
            String jurisdiction,
            String productFilter,
            String channelFilter,
            Integer retentionDays,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String createdBy,
            String approvedBy,
            UUID correlationId,
            String idempotencyKey) {}

    public record LegalHoldCommand(
            UUID tenantId,
            UUID holdId,
            String caseId,
            Map<String, Object> scope,
            String reason,
            String appliedBy,
            String approvedBy,
            UUID correlationId,
            String idempotencyKey) {}

    public record LegalHoldReleaseCommand(String releasedBy, String releaseApprovedBy, String idempotencyKey) {}

    public record RetentionPurgeCommand(UUID tenantId, LocalDate cutoff, boolean dryRun, String requestedBy, UUID correlationId, String idempotencyKey) {}

    public static class PolicyNotSatisfiedException extends RuntimeException {
        public PolicyNotSatisfiedException(String message) { super(message); }
    }

    public static class RetentionLegalHoldNotFoundException extends RuntimeException {
        public RetentionLegalHoldNotFoundException(String message) { super(message); }
    }
}
