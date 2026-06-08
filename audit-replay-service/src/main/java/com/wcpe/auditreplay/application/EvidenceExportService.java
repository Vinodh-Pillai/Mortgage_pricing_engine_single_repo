package com.wcpe.auditreplay.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.CorrelationContext;
import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.domain.EvidenceExportJob;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import com.wcpe.auditreplay.repository.EvidenceExportJobRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvidenceExportService {

    static final String EVIDENCE_EXPORT_REQUESTED_EVENT = "EvidenceExportRequested.v1";
    static final String EVIDENCE_EXPORT_READY_EVENT = "EvidenceExportReady.v1";
    static final String EVIDENCE_EXPORT_DOWNLOADED_EVENT = "EvidenceExportDownloaded.v1";
    static final String EVIDENCE_EXPORT_SCHEMA_REF = "evidence-export-v1";

    private final AuditRecordRepository auditRecordRepository;
    private final EvidenceExportJobRepository jobRepository;
    private final OutboxRecorder outboxRecorder;
    private final ObjectMapper objectMapper;
    private final EvidenceExportMetrics metrics;

    @Autowired
    public EvidenceExportService(
            AuditRecordRepository auditRecordRepository,
            EvidenceExportJobRepository jobRepository,
            OutboxRecorder outboxRecorder,
            ObjectMapper objectMapper,
            EvidenceExportMetrics metrics) {
        this.auditRecordRepository = auditRecordRepository;
        this.jobRepository = jobRepository;
        this.outboxRecorder = outboxRecorder;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    EvidenceExportService(
            AuditRecordRepository auditRecordRepository,
            EvidenceExportJobRepository jobRepository,
            OutboxRecorder outboxRecorder,
            ObjectMapper objectMapper) {
        this(auditRecordRepository, jobRepository, outboxRecorder, objectMapper, new EvidenceExportMetrics());
    }

    @Transactional
    public EvidenceExportJob requestExport(EvidenceExportCommand command) {
        long startedNanos = System.nanoTime();
        validate(command);
        String requestHash = requestHash(command);
        return jobRepository.findByTenantIdAndIdempotencyKey(command.tenantId(), command.idempotencyKey())
                .map(existing -> replayExisting(existing, requestHash))
                .orElseGet(() -> createReadyJob(command, requestHash, startedNanos));
    }

    @Transactional(readOnly = true)
    public EvidenceExportJob getExport(UUID tenantId, UUID exportId) {
        return jobRepository.findByTenantIdAndExportId(tenantId, exportId)
                .orElseThrow(() -> new EvidenceExportNotFoundException("Evidence export job not found"));
    }

    @Transactional
    public EvidenceExportJob recordDownload(UUID tenantId, UUID exportId, String actorId, String idempotencyKey) {
        EvidenceExportJob job = getExport(tenantId, exportId);
        requireText(actorId, "actorId is required");
        requireText(idempotencyKey, "Idempotency-Key is required");
        CorrelationContext.Data context = requireCorrelationContext();
        outboxRecorder.record(toOutboxCommand(job, EVIDENCE_EXPORT_DOWNLOADED_EVENT, "downloaded", actorId, idempotencyKey,
                context.causationId() == null || !context.causationId().isPresent() ? job.getExportId() : context.causationId().get()));
        metrics.recordDownload();
        return job;
    }

    public JsonNode manifest(EvidenceExportJob job) {
        return readJson(job.getManifestJson());
    }

    private EvidenceExportJob createReadyJob(EvidenceExportCommand command, String requestHash, long startedNanos) {
        try {
            List<AuditRecord> records = resolveAuditRecords(command);
            Map<String, Object> manifest = EvidenceExportManifest.build(command, records);
            byte[] sourceRefs = writeJson(sourceRefs(command));
            byte[] manifestJson = writeJson(manifest);
            String manifestHash = AuditRecorder.sha256Hex(manifestJson);
            UUID exportId = UUID.nameUUIDFromBytes((command.tenantId() + ":" + command.idempotencyKey()).getBytes(StandardCharsets.UTF_8));
            EvidenceExportJob job = EvidenceExportJob.ready(
                    command.tenantId(),
                    exportId,
                    command.requestedBy(),
                    command.purpose(),
                    command.format(),
                    command.redactionProfile(),
                    sourceRefs,
                    artifactUri(exportId),
                    manifestJson,
                    manifestHash,
                    command.expiresAt(),
                    retentionUntil(records),
                    records.stream().anyMatch(AuditRecord::isLegalHold),
                    command.correlationId(),
                    command.idempotencyKey(),
                    requestHash);
            EvidenceExportJob saved = jobRepository.save(job);
            outboxRecorder.record(toOutboxCommand(saved, EVIDENCE_EXPORT_REQUESTED_EVENT, "requested", command.requestedBy(), command.idempotencyKey(), command.causationId()));
            outboxRecorder.record(toOutboxCommand(saved, EVIDENCE_EXPORT_READY_EVENT, "ready", command.requestedBy(), command.idempotencyKey(), saved.getExportId()));
            metrics.recordReady(Duration.ofNanos(System.nanoTime() - startedNanos), manifestJson.length);
            return saved;
        } catch (RuntimeException ex) {
            metrics.recordFailure();
            throw ex;
        }
    }

    private EvidenceExportJob replayExisting(EvidenceExportJob existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException("Idempotency-Key was already used for a different evidence export request");
        }
        return existing;
    }

    private List<AuditRecord> resolveAuditRecords(EvidenceExportCommand command) {
        List<UUID> ids = command.sourceAuditRecordIds().stream().distinct().sorted().toList();
        List<AuditRecord> records = auditRecordRepository.findAllByTenantIdAndIdIn(command.tenantId(), ids);
        if (records.size() != ids.size()) {
            throw new EvidenceExportNotFoundException("One or more source audit records were not found for tenant");
        }
        return records;
    }

    private LocalDate retentionUntil(List<AuditRecord> records) {
        return records.stream()
                .map(AuditRecord::getRetentionUntil)
                .max(LocalDate::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("source audit records are required"));
    }

    private Map<String, Object> sourceRefs(EvidenceExportCommand command) {
        Map<String, Object> refs = new LinkedHashMap<>();
        refs.put("auditRecordIds", command.sourceAuditRecordIds().stream().map(Object::toString).sorted().toList());
        refs.put("replayRunIds", command.replayRunIds() == null ? List.of() : command.replayRunIds().stream().map(Object::toString).sorted().toList());
        return refs;
    }

    private OutboxRecordCommand toOutboxCommand(
            EvidenceExportJob job,
            String eventType,
            String phase,
            String actorId,
            String idempotencyKey,
            UUID causationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", job.getExportId().toString());
        payload.put("tenantId", job.getTenantId().toString());
        payload.put("status", job.getStatus().name());
        payload.put("version", 1);
        payload.put("summary", "Evidence export " + phase + " for purpose " + job.getPurpose());
        payload.put("sourceRefs", readJson(job.getSourceRefs()));
        payload.put("artifactUri", job.getArtifactUri());
        payload.put("manifestHash", job.getManifestHash());
        payload.put("redactionProfile", job.getRedactionProfile());
        payload.put("legalHold", job.isLegalHold());
        return new OutboxRecordCommand(
                job.getTenantId(),
                "evidence-export-job",
                job.getExportId().toString(),
                1L,
                eventType,
                1,
                job.getTenantId() + ":" + job.getExportId() + ":" + phase,
                job.getTenantId().toString(),
                payload,
                job.getCorrelationId(),
                causationId,
                actorId,
                idempotencyKey + ":" + phase,
                EVIDENCE_EXPORT_SCHEMA_REF);
    }

    private String requestHash(EvidenceExportCommand command) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("tenantId", command.tenantId().toString());
        canonical.put("sourceAuditRecordIds", command.sourceAuditRecordIds().stream().map(Object::toString).sorted().toList());
        canonical.put("replayRunIds", command.replayRunIds() == null ? List.of() : command.replayRunIds().stream().map(Object::toString).sorted().toList());
        canonical.put("purpose", command.purpose());
        canonical.put("format", command.format());
        canonical.put("redactionProfile", command.redactionProfile());
        canonical.put("includeSnapshots", command.includeSnapshots());
        canonical.put("includeDiffs", command.includeDiffs());
        canonical.put("includeHashes", command.includeHashes());
        canonical.put("requestedBy", command.requestedBy());
        canonical.put("expiresAt", command.expiresAt() == null ? null : command.expiresAt().toString());
        return AuditRecorder.sha256Hex(writeJson(canonical));
    }

    private String artifactUri(UUID exportId) {
        return "audit-replay://evidence-exports/" + exportId + "/manifest";
    }

    private byte[] writeJson(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Evidence export payload is not JSON serializable", ex);
        }
    }

    private JsonNode readJson(byte[] value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Evidence export JSON cannot be parsed", ex);
        }
    }

    private void validate(EvidenceExportCommand command) {
        Objects.requireNonNull(command, "command is required");
        Objects.requireNonNull(command.tenantId(), "tenantId is required");
        if (command.sourceAuditRecordIds() == null || command.sourceAuditRecordIds().isEmpty()) {
            throw new IllegalArgumentException("sourceAuditRecordIds are required");
        }
        requireText(command.purpose(), "purpose is required");
        requireText(command.format(), "format is required");
        if (!"ZIP_JSON".equals(command.format())) {
            throw new IllegalArgumentException("format currently supports ZIP_JSON; PDF_SUMMARY requires artifact renderer/provider scope");
        }
        requireText(command.redactionProfile(), "redactionProfile is required");
        requireText(command.requestedBy(), "requestedBy is required");
        Objects.requireNonNull(command.correlationId(), "correlationId is required");
        requireText(command.idempotencyKey(), "Idempotency-Key is required");
        CorrelationContext.Data context = requireCorrelationContext();
        if (!command.correlationId().equals(context.correlationId().getValue())) {
            throw new IllegalArgumentException("correlationId must match current CorrelationContext");
        }
    }

    private CorrelationContext.Data requireCorrelationContext() {
        CorrelationContext.Data context = CorrelationContext.get();
        if (context == null || context.correlationId() == null || context.requestId() == null) {
            throw new IllegalStateException("CorrelationContext is required");
        }
        return context;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    public static class EvidenceExportNotFoundException extends RuntimeException {
        public EvidenceExportNotFoundException(String message) { super(message); }
    }

    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) { super(message); }
    }
}
