package com.wcpe.auditreplay.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.CorrelationContext;
import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.domain.LockReplayArtifact;
import com.wcpe.auditreplay.domain.LockReplayMode;
import com.wcpe.auditreplay.domain.LockReplayStatus;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import com.wcpe.auditreplay.repository.LockReplayArtifactRepository;
import com.wcpe.auditreplay.repository.LockReplayRunRepository;
import com.wcpe.auditreplay.vo.CausationId;
import com.wcpe.auditreplay.vo.CorrelationId;
import com.wcpe.auditreplay.vo.RequestId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LockReplayAssemblerTest {

    private final AuditRecordRepository auditRecordRepository = mock(AuditRecordRepository.class);
    private final LockReplayRunRepository runRepository = mock(LockReplayRunRepository.class);
    private final LockReplayArtifactRepository artifactRepository = mock(LockReplayArtifactRepository.class);
    private final OutboxRecorder outboxRecorder = mock(OutboxRecorder.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final LockReplayService service = new LockReplayService(
            auditRecordRepository, runRepository, artifactRepository, outboxRecorder, objectMapper);

    @AfterEach
    void clearCorrelationContext() {
        CorrelationContext.clear();
    }

    @Test
    void rejectsMissingMarketSnapshot() {
        AuditRecord source = sourceRecord(UUID.randomUUID(), "source-lock-hash-1", "{\"lockId\":\"lock-123\"}");
        when(runRepository.findByTenantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(auditRecordRepository.findByTenantIdAndId(source.getTenantId(), source.getId())).thenReturn(Optional.of(source));
        LockReplayCommand command = command(source, source.getIntegrityHash(), "ELIGIBILITY");
        setCorrelationContext(command);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> service.startReplay(command));

        assertEquals("marketSnapshotRef is required for lock replay", failure.getMessage());
    }

    private LockReplayCommand command(AuditRecord source, String expectedHash, String decisionType) {
        return new LockReplayCommand(
                source.getTenantId(),
                source.getId(),
                source.getSubjectId(),
                decisionType,
                LockReplayMode.VERIFY,
                "prove lock replay determinism",
                "audit-user-1",
                expectedHash,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "idem-lock-replay-assembler");
    }

    private void setCorrelationContext(LockReplayCommand command) {
        CorrelationContext.set(
                CorrelationId.of(command.correlationId().toString()),
                CausationId.ofNullable(null),
                RequestId.of(command.requestId().toString()));
    }

    private AuditRecord sourceRecord(UUID tenantId, String integrityHash, String snapshotJson) {
        return AuditRecord.create(
                tenantId,
                UUID.randomUUID(),
                "LOCK_EXTENDED",
                "lock",
                "lock-123",
                1L,
                "SERVICE",
                "lock-service",
                "Lock Service",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "source-ip-hash",
                "user-agent-hash",
                null,
                null,
                snapshotJson.getBytes(StandardCharsets.UTF_8),
                "mortgage-pricing-default-v1",
                "{\"lockPolicyVersionRefs\":[\"lock-policy-v1\"],\"extensionPolicyRef\":\"extension-policy-v1\"}".getBytes(StandardCharsets.UTF_8),
                "SUCCESS",
                null,
                Instant.now(),
                LocalDate.now().plusYears(7),
                false,
                integrityHash,
                null);
    }
}
