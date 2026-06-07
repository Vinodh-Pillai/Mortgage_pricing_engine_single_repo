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
import com.wcpe.auditreplay.domain.EvidenceExportJob;
import com.wcpe.auditreplay.domain.EvidenceExportStatus;
import com.wcpe.auditreplay.domain.OutboxEvent;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import com.wcpe.auditreplay.repository.EvidenceExportJobRepository;
import com.wcpe.auditreplay.vo.CausationId;
import com.wcpe.auditreplay.vo.CorrelationId;
import com.wcpe.auditreplay.vo.RequestId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EvidenceExportServiceTest {

    private final AuditRecordRepository auditRecordRepository = mock(AuditRecordRepository.class);
    private final EvidenceExportJobRepository jobRepository = mock(EvidenceExportJobRepository.class);
    private final OutboxRecorder outboxRecorder = mock(OutboxRecorder.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final EvidenceExportService service = new EvidenceExportService(
            auditRecordRepository, jobRepository, outboxRecorder, objectMapper);

    @AfterEach
    void clearCorrelationContext() {
        CorrelationContext.clear();
    }

    @Test
    void buildsZipJsonManifestAndAuditEvents() {
        AuditRecord source = sourceRecord(UUID.randomUUID(), "hash-1", true);
        EvidenceExportCommand command = command(source);
        setCorrelationContext(command);
        when(jobRepository.findByTenantIdAndIdempotencyKey(command.tenantId(), command.idempotencyKey())).thenReturn(Optional.empty());
        when(auditRecordRepository.findAllByTenantIdAndIdIn(command.tenantId(), List.of(source.getId()))).thenReturn(List.of(source));
        when(jobRepository.save(any(EvidenceExportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRecorder.record(any(OutboxRecordCommand.class))).thenReturn(mock(OutboxEvent.class));

        EvidenceExportJob job = service.requestExport(command);

        assertEquals(EvidenceExportStatus.READY, job.getStatus());
        assertEquals("ZIP_JSON", job.getFormat());
        assertEquals(true, job.isLegalHold());
        assertEquals("audit-replay://evidence-exports/" + job.getExportId() + "/manifest", job.getArtifactUri());
        verify(outboxRecorder, org.mockito.Mockito.times(2)).record(any(OutboxRecordCommand.class));
    }

    @Test
    void rejectsIdempotencyReuseWithDifferentPayload() {
        AuditRecord source = sourceRecord(UUID.randomUUID(), "hash-2", false);
        EvidenceExportCommand command = command(source);
        setCorrelationContext(command);
        EvidenceExportJob existing = EvidenceExportJob.ready(
                command.tenantId(),
                UUID.randomUUID(),
                command.requestedBy(),
                command.purpose(),
                command.format(),
                command.redactionProfile(),
                "{\"auditRecordIds\":[]}".getBytes(StandardCharsets.UTF_8),
                "audit-replay://evidence-exports/existing/manifest",
                "{\"schemaVersion\":\"evidence-export-manifest-v1\"}".getBytes(StandardCharsets.UTF_8),
                "manifest-hash",
                null,
                LocalDate.parse("2033-06-07"),
                false,
                command.correlationId(),
                command.idempotencyKey(),
                "different-request-hash");
        when(jobRepository.findByTenantIdAndIdempotencyKey(command.tenantId(), command.idempotencyKey())).thenReturn(Optional.of(existing));

        EvidenceExportService.IdempotencyConflictException failure = assertThrows(
                EvidenceExportService.IdempotencyConflictException.class,
                () -> service.requestExport(command));

        assertEquals("Idempotency-Key was already used for a different evidence export request", failure.getMessage());
    }

    @Test
    void recordsDownloadAuditEvent() {
        AuditRecord source = sourceRecord(UUID.randomUUID(), "hash-3", false);
        EvidenceExportCommand command = command(source);
        EvidenceExportJob job = EvidenceExportJob.ready(
                command.tenantId(),
                UUID.randomUUID(),
                command.requestedBy(),
                command.purpose(),
                command.format(),
                command.redactionProfile(),
                "{\"auditRecordIds\":[]}".getBytes(StandardCharsets.UTF_8),
                "audit-replay://evidence-exports/ref/manifest",
                "{\"schemaVersion\":\"evidence-export-manifest-v1\"}".getBytes(StandardCharsets.UTF_8),
                "manifest-hash",
                null,
                LocalDate.parse("2033-06-07"),
                false,
                command.correlationId(),
                command.idempotencyKey(),
                "request-hash");
        setCorrelationContext(command);
        when(jobRepository.findByTenantIdAndExportId(job.getTenantId(), job.getExportId())).thenReturn(Optional.of(job));
        when(outboxRecorder.record(any(OutboxRecordCommand.class))).thenReturn(mock(OutboxEvent.class));

        EvidenceExportJob downloaded = service.recordDownload(job.getTenantId(), job.getExportId(), "audit-user-1", "download-idem");

        assertEquals(job.getExportId(), downloaded.getExportId());
        verify(outboxRecorder).record(any(OutboxRecordCommand.class));
    }

    private EvidenceExportCommand command(AuditRecord source) {
        return new EvidenceExportCommand(
                source.getTenantId(),
                List.of(source.getId()),
                List.of(UUID.randomUUID()),
                "regulator support package",
                "ZIP_JSON",
                "REGULATOR_SUMMARY",
                true,
                true,
                true,
                "audit-user-1",
                UUID.randomUUID(),
                null,
                "idem-evidence-export-1",
                null,
                Map.of("caseId", "case-123"));
    }

    private void setCorrelationContext(EvidenceExportCommand command) {
        CorrelationContext.set(
                CorrelationId.of(command.correlationId().toString()),
                CausationId.ofNullable(command.causationId()),
                RequestId.of(UUID.randomUUID().toString()));
    }

    private AuditRecord sourceRecord(UUID tenantId, String integrityHash, boolean legalHold) {
        return AuditRecord.create(
                tenantId,
                UUID.randomUUID(),
                "QUOTE_REPLAY_COMPLETED",
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
                "REGULATOR_SUMMARY",
                "{\"retentionPolicy\":\"tenant-config-ref\"}".getBytes(StandardCharsets.UTF_8),
                "SUCCESS",
                null,
                Instant.now(),
                LocalDate.parse("2033-06-07"),
                legalHold,
                integrityHash,
                null);
    }
}
