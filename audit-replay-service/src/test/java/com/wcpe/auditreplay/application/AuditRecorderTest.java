package com.wcpe.auditreplay.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.CorrelationContext;
import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.domain.AuditSnapshot;
import com.wcpe.auditreplay.domain.OutboxEvent;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import com.wcpe.auditreplay.repository.AuditSnapshotRepository;
import com.wcpe.auditreplay.vo.CausationId;
import com.wcpe.auditreplay.vo.CorrelationId;
import com.wcpe.auditreplay.vo.RequestId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuditRecorderTest {

    private final AuditRecordRepository recordRepository = mock(AuditRecordRepository.class);
    private final AuditSnapshotRepository snapshotRepository = mock(AuditSnapshotRepository.class);
    private final OutboxRecorder outboxRecorder = mock(OutboxRecorder.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void clearCorrelationContext() {
        CorrelationContext.clear();
    }

    @Test
    void recordsAuditLogWithSnapshotsHashChainAndOutbox() {
        when(recordRepository.findByTenantIdAndRequestId(any(), any())).thenReturn(Optional.empty());
        when(recordRepository.findTopByTenantIdOrderByOccurredAtDescCreatedAtDesc(any())).thenReturn(Optional.empty());
        when(recordRepository.save(any(AuditRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshotRepository.save(any(AuditSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRecorder.record(any(OutboxRecordCommand.class))).thenReturn(mock(OutboxEvent.class));
        AuditRecorder recorder = new AuditRecorder(recordRepository, snapshotRepository, outboxRecorder, objectMapper);

        AuditRecordCommand command = command(UUID.randomUUID());
        setCorrelationContext(command);
        AuditRecord record = recorder.record(command);

        assertNotNull(record.getId());
        assertNotNull(record.getBeforeRef());
        assertNotNull(record.getAfterRef());
        assertNotNull(record.getIntegrityHash());
        assertEquals("QUOTE_CREATED", record.getAction());
        verify(snapshotRepository, org.mockito.Mockito.times(2)).save(any(AuditSnapshot.class));
        verify(outboxRecorder).record(any(OutboxRecordCommand.class));
    }

    @Test
    void masksBorrowerAndFinancialSensitiveFields() {
        when(recordRepository.findByTenantIdAndRequestId(any(), any())).thenReturn(Optional.empty());
        when(recordRepository.findTopByTenantIdOrderByOccurredAtDescCreatedAtDesc(any())).thenReturn(Optional.empty());
        when(recordRepository.save(any(AuditRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshotRepository.save(any(AuditSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRecorder.record(any(OutboxRecordCommand.class))).thenReturn(mock(OutboxEvent.class));
        AuditRecorder recorder = new AuditRecorder(recordRepository, snapshotRepository, outboxRecorder, objectMapper);
        AuditRecordCommand command = commandWithSnapshot(
                UUID.randomUUID(),
                "{\"borrowerName\":\"Alice Applicant\",\"financial\":{\"annualIncome\":123000,\"creditScore\":740},\"quoteId\":\"quote-123\"}");
        setCorrelationContext(command);

        AuditRecord record = recorder.record(command);

        String persistedSnapshot = new String(record.getSnapshotJson(), StandardCharsets.UTF_8);
        assertFalse(persistedSnapshot.contains("Alice Applicant"));
        assertFalse(persistedSnapshot.contains("123000"));
        assertFalse(persistedSnapshot.contains("740"));
        assertTrue(persistedSnapshot.contains(AuditRedactionPolicy.MASK));
        assertTrue(persistedSnapshot.contains("quote-123"));
    }

    @Test
    void recordsAuditMetricsWithRequiredNames() {
        when(recordRepository.findByTenantIdAndRequestId(any(), any())).thenReturn(Optional.empty());
        when(recordRepository.findTopByTenantIdOrderByOccurredAtDescCreatedAtDesc(any())).thenReturn(Optional.empty());
        when(recordRepository.save(any(AuditRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshotRepository.save(any(AuditSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRecorder.record(any(OutboxRecordCommand.class))).thenReturn(mock(OutboxEvent.class));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditRecorder recorder = new AuditRecorder(
                recordRepository,
                snapshotRepository,
                outboxRecorder,
                objectMapper,
                new AuditRedactionPolicy(objectMapper),
                new AuditRecorderMetrics(registry));
        AuditRecordCommand command = command(UUID.randomUUID());
        setCorrelationContext(command);

        recorder.record(command);

        assertEquals(1.0, registry.find(AuditRecorderMetrics.RECORDS_WRITTEN).counter().count());
        assertEquals(1, registry.find(AuditRecorderMetrics.WRITE_LATENCY).timer().count());
        assertNotNull(registry.find(AuditRecorderMetrics.REDACTION_FAILURES).counter());
        assertNotNull(registry.find(AuditRecorderMetrics.HASH_CHAIN_BREAKS).counter());
    }

    @Test
    void rejectsUnparseableSnapshotAndRecordsRedactionFailure() {
        when(recordRepository.findByTenantIdAndRequestId(any(), any())).thenReturn(Optional.empty());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditRecorder recorder = new AuditRecorder(
                recordRepository,
                snapshotRepository,
                outboxRecorder,
                objectMapper,
                new AuditRedactionPolicy(objectMapper),
                new AuditRecorderMetrics(registry));
        AuditRecordCommand command = commandWithSnapshot(UUID.randomUUID(), "not-json");
        setCorrelationContext(command);

        assertThrows(IllegalArgumentException.class, () -> recorder.record(command));
        assertEquals(1.0, registry.find(AuditRecorderMetrics.REDACTION_FAILURES).counter().count());
    }

    @Test
    void rejectsRecordWithoutCorrelationContextAtPersistenceBoundary() {
        AuditRecorder recorder = new AuditRecorder(recordRepository, snapshotRepository, outboxRecorder, objectMapper);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> recorder.record(command(UUID.randomUUID())));

        assertEquals("CorrelationId is missing — cannot cross persistence boundary", failure.getMessage());
    }

    @Test
    void rejectsRecordWhenCommandCorrelationDiffersFromCurrentContext() {
        AuditRecorder recorder = new AuditRecorder(recordRepository, snapshotRepository, outboxRecorder, objectMapper);
        AuditRecordCommand command = command(UUID.randomUUID());
        CorrelationContext.set(CorrelationId.generate(), CausationId.ofNullable(null), RequestId.of(command.requestId().toString()));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> recorder.record(command));

        assertEquals("correlationId must match current CorrelationContext", failure.getMessage());
    }

    @Test
    void replaysExistingRequestWithoutDuplicateOutbox() {
        UUID tenantId = UUID.randomUUID();
        AuditRecord existing = AuditRecord.create(
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
                null,
                null,
                null,
                null,
                "{\"borrower\":\"REDACTED\"}".getBytes(StandardCharsets.UTF_8),
                "mortgage-pricing-default-v1",
                "{\"retentionPolicy\":\"tenant-config-ref\"}".getBytes(StandardCharsets.UTF_8),
                "SUCCESS",
                null,
                Instant.now(),
                LocalDate.now().plusYears(7),
                false,
                "existing-hash",
                null);
        when(recordRepository.findByTenantIdAndRequestId(any(), any())).thenReturn(Optional.of(existing));
        AuditRecorder recorder = new AuditRecorder(recordRepository, snapshotRepository, outboxRecorder, objectMapper);
        AuditRecordCommand command = command(tenantId);
        setCorrelationContext(command);

        AuditRecord replayed = recorder.record(command);

        assertEquals(existing.getId(), replayed.getId());
        org.mockito.Mockito.verifyNoInteractions(snapshotRepository, outboxRecorder);
    }

    private AuditRecordCommand command(UUID tenantId) {
        return commandWithSnapshot(tenantId, "{\"borrower\":\"REDACTED\"}");
    }

    private AuditRecordCommand commandWithSnapshot(UUID tenantId, String snapshotJson) {
        return new AuditRecordCommand(
                tenantId,
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
                "{\"before\":\"encrypted\"}".getBytes(StandardCharsets.UTF_8),
                "{\"after\":\"encrypted\"}".getBytes(StandardCharsets.UTF_8),
                snapshotJson.getBytes(StandardCharsets.UTF_8),
                "kms://tenant/audit-key-ref",
                "mortgage-pricing-default-v1",
                "{\"retentionPolicy\":\"tenant-config-ref\"}".getBytes(StandardCharsets.UTF_8),
                "SUCCESS",
                null,
                Instant.now(),
                LocalDate.now().plusYears(7),
                false,
                "idem-audit-record");
    }

    private void setCorrelationContext(AuditRecordCommand command) {
        CorrelationContext.set(
                CorrelationId.of(command.correlationId().toString()),
                CausationId.ofNullable(command.causationId()),
                RequestId.of(command.requestId().toString()));
    }
}
