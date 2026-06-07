package com.wcpe.auditreplay.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.CorrelationContext;
import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.domain.LockReplayArtifact;
import com.wcpe.auditreplay.domain.LockReplayMode;
import com.wcpe.auditreplay.domain.LockReplayStatus;
import com.wcpe.auditreplay.domain.OutboxEvent;
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

class LockReplayComparatorTest {

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
    void classifiesExpirationMismatch() {
        AuditRecord source = sourceRecord(UUID.randomUUID(), "source-lock-hash-2");
        when(runRepository.findByTenantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(auditRecordRepository.findTopByTenantIdAndSubjectTypeAndSubjectIdOrderByOccurredAtDescCreatedAtDesc(
                source.getTenantId(), "lock", source.getSubjectId())).thenReturn(Optional.of(source));
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(artifactRepository.save(any(LockReplayArtifact.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRecorder.record(any(OutboxRecordCommand.class))).thenReturn(mock(OutboxEvent.class));
        LockReplayCommand command = new LockReplayCommand(
                source.getTenantId(),
                null,
                source.getSubjectId(),
                "EXPIRATION",
                LockReplayMode.DIAGNOSE,
                "investigate expiration drift",
                "audit-user-1",
                "different-expected-hash",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "idem-lock-replay-comparator");
        setCorrelationContext(command);

        LockReplayResult result = service.startReplay(command);

        assertEquals(LockReplayStatus.MISMATCH, result.run().getStatus());
        assertEquals("EXPIRATION_MISMATCH", result.run().getMismatchCode());
        assertEquals("MISMATCH", result.diff().get("classification").asText());
        assertEquals(false, result.diff().get("lockStateMutated").asBoolean());
        assertEquals(false, result.diff().get("currentMarketDataUsed").asBoolean());
        verify(outboxRecorder, org.mockito.Mockito.times(2)).record(any(OutboxRecordCommand.class));
    }

    private void setCorrelationContext(LockReplayCommand command) {
        CorrelationContext.set(
                CorrelationId.of(command.correlationId().toString()),
                CausationId.ofNullable(null),
                RequestId.of(command.requestId().toString()));
    }

    private AuditRecord sourceRecord(UUID tenantId, String integrityHash) {
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
                "{\"lockId\":\"lock-123\",\"marketSnapshotRef\":\"market-snapshot-2026-06-07\"}".getBytes(StandardCharsets.UTF_8),
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
