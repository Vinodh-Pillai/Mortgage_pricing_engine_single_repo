package com.wcpe.auditreplay.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditIntegrityHashService {

    public static final String HASH_ALGORITHM = "SHA-256";
    public static final String CANONICALIZATION_VERSION = "audit-record-canonical-v1";

    private final AuditRecordRepository auditRecordRepository;
    private final ObjectMapper objectMapper;

    public AuditIntegrityHashService(AuditRecordRepository auditRecordRepository, ObjectMapper objectMapper) {
        this.auditRecordRepository = auditRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AuditIntegrityVerification verifyTenantChain(UUID tenantId, Instant from, Instant to) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(to, "to is required");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }

        List<AuditRecord> records = auditRecordRepository
                .findAllByTenantIdAndOccurredAtBetweenOrderByOccurredAtAscCreatedAtAscIdAsc(tenantId, from, to);
        int brokenCount = 0;
        UUID firstBreakRecordId = null;
        String expectedPreviousHash = records.isEmpty()
                ? null
                : auditRecordRepository
                        .findTopByTenantIdAndOccurredAtBeforeOrderByOccurredAtDescCreatedAtDescIdDesc(
                                tenantId, records.get(0).getOccurredAt())
                        .map(AuditRecord::getIntegrityHash)
                        .orElse(null);
        for (AuditRecord record : records) {
            String expectedHash = hash(record, expectedPreviousHash, objectMapper);
            boolean missingHash = isBlank(record.getIntegrityHash());
            boolean brokenHash = !missingHash && !record.getIntegrityHash().equals(expectedHash);
            boolean brokenPreviousHash = !Objects.equals(record.getPreviousHash(), expectedPreviousHash);
            if (missingHash || brokenHash || brokenPreviousHash) {
                brokenCount++;
                if (firstBreakRecordId == null) {
                    firstBreakRecordId = record.getId();
                }
            }
            expectedPreviousHash = record.getIntegrityHash();
        }
        return new AuditIntegrityVerification(
                tenantId,
                from,
                to,
                records.size(),
                brokenCount,
                firstBreakRecordId,
                brokenCount == 0 ? "VERIFIED" : "BROKEN",
                HASH_ALGORITHM,
                CANONICALIZATION_VERSION);
    }

    static String hash(AuditRecordCommand command, String previousHash, UUID beforeRef, UUID afterRef, ObjectMapper objectMapper) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("tenantId", command.tenantId().toString());
        canonical.put("requestId", command.requestId().toString());
        canonical.put("action", command.action());
        canonical.put("subjectType", command.subjectType());
        canonical.put("subjectId", command.subjectId());
        canonical.put("subjectVersion", command.subjectVersion());
        canonical.put("actorType", command.actorType());
        canonical.put("actorId", command.actorId());
        canonical.put("correlationId", command.correlationId().toString());
        canonical.put("beforeRef", beforeRef == null ? null : beforeRef.toString());
        canonical.put("afterRef", afterRef == null ? null : afterRef.toString());
        canonical.put("snapshotHash", AuditRecorder.sha256Hex(command.snapshotJson()));
        canonical.put("configVersionRefsHash", AuditRecorder.sha256Hex(command.configVersionRefsJson()));
        canonical.put("result", command.result());
        canonical.put("reasonCode", command.reasonCode());
        canonical.put("occurredAt", command.occurredAt().toString());
        canonical.put("retentionUntil", command.retentionUntil().toString());
        canonical.put("legalHold", command.legalHold());
        canonical.put("previousHash", previousHash);
        return writeCanonicalHash(canonical, objectMapper);
    }

    static String hash(AuditRecord record, String previousHash, ObjectMapper objectMapper) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("tenantId", record.getTenantId().toString());
        canonical.put("requestId", record.getRequestId().toString());
        canonical.put("action", record.getAction());
        canonical.put("subjectType", record.getSubjectType());
        canonical.put("subjectId", record.getSubjectId());
        canonical.put("subjectVersion", record.getSubjectVersion());
        canonical.put("actorType", record.getActorType());
        canonical.put("actorId", record.getActorId());
        canonical.put("correlationId", record.getCorrelationId().toString());
        canonical.put("beforeRef", record.getBeforeRef() == null ? null : record.getBeforeRef().toString());
        canonical.put("afterRef", record.getAfterRef() == null ? null : record.getAfterRef().toString());
        canonical.put("snapshotHash", AuditRecorder.sha256Hex(record.getSnapshotJson()));
        canonical.put("configVersionRefsHash", AuditRecorder.sha256Hex(record.getConfigVersionRefs()));
        canonical.put("result", record.getResult());
        canonical.put("reasonCode", record.getReasonCode());
        canonical.put("occurredAt", record.getOccurredAt().toString());
        canonical.put("retentionUntil", record.getRetentionUntil().toString());
        canonical.put("legalHold", record.isLegalHold());
        canonical.put("previousHash", previousHash);
        return writeCanonicalHash(canonical, objectMapper);
    }

    private static String writeCanonicalHash(Map<String, Object> canonical, ObjectMapper objectMapper) {
        try {
            return AuditRecorder.sha256Hex(objectMapper.writeValueAsBytes(canonical));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Audit record canonical hash payload is not JSON serializable", ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record AuditIntegrityVerification(
            UUID tenantId,
            Instant from,
            Instant to,
            int checkedCount,
            int brokenCount,
            UUID firstBreakRecordId,
            String status,
            String hashAlgorithm,
            String canonicalizationVersion) {
    }
}
