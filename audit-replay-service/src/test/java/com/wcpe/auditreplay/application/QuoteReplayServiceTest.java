package com.wcpe.auditreplay.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.CorrelationContext;
import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.domain.OutboxEvent;
import com.wcpe.auditreplay.domain.QuoteReplayArtifact;
import com.wcpe.auditreplay.domain.QuoteReplayMode;
import com.wcpe.auditreplay.domain.QuoteReplayRun;
import com.wcpe.auditreplay.domain.QuoteReplayStatus;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import com.wcpe.auditreplay.repository.QuoteReplayArtifactRepository;
import com.wcpe.auditreplay.repository.QuoteReplayRunRepository;
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

class QuoteReplayServiceTest {

    private final AuditRecordRepository auditRecordRepository = mock(AuditRecordRepository.class);
    private final QuoteReplayRunRepository runRepository = mock(QuoteReplayRunRepository.class);
    private final QuoteReplayArtifactRepository artifactRepository = mock(QuoteReplayArtifactRepository.class);
    private final OutboxRecorder outboxRecorder = mock(OutboxRecorder.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final QuoteReplayService service = new QuoteReplayService(
            auditRecordRepository, runRepository, artifactRepository, outboxRecorder, objectMapper);

    @AfterEach
    void clearCorrelationContext() {
        CorrelationContext.clear();
    }

    @Test
    void completesMatchForGoldenQuoteWithoutMutatingQuoteState() {
        AuditRecord source = sourceRecord(UUID.randomUUID(), "source-hash-1");
        when(runRepository.findByTenantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(auditRecordRepository.findByTenantIdAndId(source.getTenantId(), source.getId())).thenReturn(Optional.of(source));
        when(runRepository.save(any(QuoteReplayRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(artifactRepository.save(any(QuoteReplayArtifact.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRecorder.record(any(OutboxRecordCommand.class))).thenReturn(mock(OutboxEvent.class));
        QuoteReplayCommand command = command(source, source.getIntegrityHash());
        setCorrelationContext(command);

        QuoteReplayResult result = service.startReplay(command);

        assertEquals(QuoteReplayStatus.MATCH, result.run().getStatus());
        assertEquals(source.getIntegrityHash(), result.run().getOriginalHash());
        assertEquals(source.getIntegrityHash(), result.run().getReplayHash());
        assertEquals(false, result.diff().get("quoteStateMutated").asBoolean());
        verify(outboxRecorder, org.mockito.Mockito.times(2)).record(any(OutboxRecordCommand.class));
    }

    @Test
    void classifiesHashMismatchAndLedgerDelta() {
        AuditRecord source = sourceRecord(UUID.randomUUID(), "source-hash-2");
        when(runRepository.findByTenantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(auditRecordRepository.findTopByTenantIdAndSubjectTypeAndSubjectIdOrderByOccurredAtDescCreatedAtDesc(
                source.getTenantId(), "quote", source.getSubjectId())).thenReturn(Optional.of(source));
        when(runRepository.save(any(QuoteReplayRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(artifactRepository.save(any(QuoteReplayArtifact.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRecorder.record(any(OutboxRecordCommand.class))).thenReturn(mock(OutboxEvent.class));
        QuoteReplayCommand command = new QuoteReplayCommand(
                source.getTenantId(),
                null,
                source.getSubjectId(),
                QuoteReplayMode.DIAGNOSE,
                "investigate expected hash drift",
                "audit-user-1",
                "different-expected-hash",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "idem-quote-replay-2");
        setCorrelationContext(command);

        QuoteReplayResult result = service.startReplay(command);

        assertEquals(QuoteReplayStatus.MISMATCH, result.run().getStatus());
        assertEquals("OUTPUT_HASH_MISMATCH", result.run().getMismatchCode());
        assertEquals("MISMATCH", result.diff().get("classification").asText());
        assertEquals("expectedHash does not match immutable audit replay hash", result.diff().get("ledgerDiff").get(0).asText());
    }

    @Test
    void rejectsIdempotencyKeyReuseWithDifferentCommand() {
        AuditRecord source = sourceRecord(UUID.randomUUID(), "source-hash-3");
        QuoteReplayCommand command = command(source, source.getIntegrityHash());
        QuoteReplayRun existing = QuoteReplayRun.completed(
                command.tenantId(), UUID.randomUUID(), source.getSubjectId(), source.getId(), command.requestedBy(),
                "different reason", command.mode(), QuoteReplayStatus.MATCH, source.getIntegrityHash(), source.getIntegrityHash(),
                null, source.getConfigVersionRefs(), "audit-record:" + source.getId(), Instant.now(), Instant.now(),
                command.correlationId(), command.idempotencyKey(), "different-request-hash");
        when(runRepository.findByTenantIdAndIdempotencyKey(command.tenantId(), command.idempotencyKey())).thenReturn(Optional.of(existing));
        setCorrelationContext(command);

        QuoteReplayService.IdempotencyConflictException failure = assertThrows(
                QuoteReplayService.IdempotencyConflictException.class,
                () -> service.startReplay(command));

        assertEquals("Idempotency-Key was already used for a different quote replay request", failure.getMessage());
    }

    private QuoteReplayCommand command(AuditRecord source, String expectedHash) {
        return new QuoteReplayCommand(
                source.getTenantId(),
                source.getId(),
                source.getSubjectId(),
                QuoteReplayMode.VERIFY,
                "prove quote replay determinism",
                "audit-user-1",
                expectedHash,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "idem-quote-replay-1");
    }

    private void setCorrelationContext(QuoteReplayCommand command) {
        CorrelationContext.set(
                CorrelationId.of(command.correlationId().toString()),
                CausationId.ofNullable(null),
                RequestId.of(command.requestId().toString()));
    }

    private AuditRecord sourceRecord(UUID tenantId, String integrityHash) {
        return AuditRecord.create(
                tenantId,
                UUID.randomUUID(),
                "QUOTE_CREATED",
                "quote",
                "quote-123",
                1L,
                "SERVICE",
                "quote-service",
                "Quote Service",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "source-ip-hash",
                "user-agent-hash",
                null,
                null,
                "{\"quoteId\":\"quote-123\",\"borrower\":\"REDACTED\"}".getBytes(StandardCharsets.UTF_8),
                "mortgage-pricing-default-v1",
                "{\"pricingConfig\":\"version-ref-1\"}".getBytes(StandardCharsets.UTF_8),
                "SUCCESS",
                null,
                Instant.now(),
                LocalDate.now().plusYears(7),
                false,
                integrityHash,
                null);
    }
}
