package com.wcpe.auditreplay.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditIntegrityHashServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void sameCanonicalInputSameHash() {
        AuditRecordCommand command = command(UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-06-07T10:00:00Z"));

        String first = AuditIntegrityHashService.hash(command, "previous-hash", null, null, objectMapper);
        String second = AuditIntegrityHashService.hash(command, "previous-hash", null, null, objectMapper);

        assertEquals(first, second);
    }

    @Test
    void detectsChangedSnapshot() {
        UUID tenantId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-06-07T10:00:00Z");

        String original = AuditIntegrityHashService.hash(command(tenantId, requestId, occurredAt), null, null, null, objectMapper);
        String changed = AuditIntegrityHashService.hash(changedSnapshotCommand(tenantId, requestId, occurredAt), null, null, null, objectMapper);

        assertNotEquals(original, changed);
    }

    @Test
    void verifiesContinuousTenantChain() {
        UUID tenantId = UUID.randomUUID();
        AuditRecord first = record(tenantId, Instant.parse("2026-06-07T10:00:00Z"), null, false);
        AuditRecord second = record(tenantId, Instant.parse("2026-06-07T10:01:00Z"), first.getIntegrityHash(), false);
        Instant from = Instant.parse("2026-06-07T09:59:00Z");
        Instant to = Instant.parse("2026-06-07T10:02:00Z");
        AuditRecordRepository repository = mock(AuditRecordRepository.class);
        when(repository.findAllByTenantIdAndOccurredAtBetweenOrderByOccurredAtAscCreatedAtAscIdAsc(tenantId, from, to))
                .thenReturn(List.of(first, second));
        AuditIntegrityHashService service = new AuditIntegrityHashService(repository, objectMapper);

        AuditIntegrityHashService.AuditIntegrityVerification result = service.verifyTenantChain(tenantId, from, to);

        assertEquals("VERIFIED", result.status());
        assertEquals(2, result.checkedCount());
        assertEquals(0, result.brokenCount());
        assertNull(result.firstBreakRecordId());
        assertEquals("SHA-256", result.hashAlgorithm());
        assertEquals("audit-record-canonical-v1", result.canonicalizationVersion());
    }

    @Test
    void verifiesNonGenesisTenantRangeFromPrecedingRecord() {
        UUID tenantId = UUID.randomUUID();
        AuditRecord preceding = record(tenantId, Instant.parse("2026-06-07T09:59:00Z"), null, false);
        AuditRecord first = record(tenantId, Instant.parse("2026-06-07T10:00:00Z"), preceding.getIntegrityHash(), false);
        AuditRecord second = record(tenantId, Instant.parse("2026-06-07T10:01:00Z"), first.getIntegrityHash(), false);
        Instant from = Instant.parse("2026-06-07T10:00:00Z");
        Instant to = Instant.parse("2026-06-07T10:02:00Z");
        AuditRecordRepository repository = mock(AuditRecordRepository.class);
        when(repository.findAllByTenantIdAndOccurredAtBetweenOrderByOccurredAtAscCreatedAtAscIdAsc(tenantId, from, to))
                .thenReturn(List.of(first, second));
        when(repository.findTopByTenantIdAndOccurredAtBeforeOrderByOccurredAtDescCreatedAtDescIdDesc(
                tenantId, first.getOccurredAt()))
                .thenReturn(Optional.of(preceding));
        AuditIntegrityHashService service = new AuditIntegrityHashService(repository, objectMapper);

        AuditIntegrityHashService.AuditIntegrityVerification result = service.verifyTenantChain(tenantId, from, to);

        assertEquals("VERIFIED", result.status());
        assertEquals(2, result.checkedCount());
        assertEquals(0, result.brokenCount());
        assertNull(result.firstBreakRecordId());
    }

    @Test
    void reportsFirstBreak() {
        UUID tenantId = UUID.randomUUID();
        AuditRecord first = record(tenantId, Instant.parse("2026-06-07T10:00:00Z"), null, false);
        AuditRecord second = record(tenantId, Instant.parse("2026-06-07T10:01:00Z"), "wrong-previous-hash", false);
        Instant from = Instant.parse("2026-06-07T09:59:00Z");
        Instant to = Instant.parse("2026-06-07T10:02:00Z");
        AuditRecordRepository repository = mock(AuditRecordRepository.class);
        when(repository.findAllByTenantIdAndOccurredAtBetweenOrderByOccurredAtAscCreatedAtAscIdAsc(tenantId, from, to))
                .thenReturn(List.of(first, second));
        AuditIntegrityHashService service = new AuditIntegrityHashService(repository, objectMapper);

        AuditIntegrityHashService.AuditIntegrityVerification result = service.verifyTenantChain(tenantId, from, to);

        assertEquals("BROKEN", result.status());
        assertEquals(1, result.brokenCount());
        assertEquals(second.getId(), result.firstBreakRecordId());
    }

    private AuditRecord record(UUID tenantId, Instant occurredAt, String previousHash, boolean changedSnapshot) {
        AuditRecordCommand command = changedSnapshot
                ? changedSnapshotCommand(tenantId, UUID.randomUUID(), occurredAt)
                : command(tenantId, UUID.randomUUID(), occurredAt);
        String hash = AuditIntegrityHashService.hash(command, previousHash, null, null, objectMapper);
        return AuditRecord.create(
                command.tenantId(),
                UUID.nameUUIDFromBytes((command.tenantId() + ":" + command.requestId()).getBytes(StandardCharsets.UTF_8)),
                command.action(),
                command.subjectType(),
                command.subjectId(),
                command.subjectVersion(),
                command.actorType(),
                command.actorId(),
                command.actorDisplay(),
                command.correlationId(),
                command.causationId(),
                command.requestId(),
                command.sourceIpHash(),
                command.userAgentHash(),
                null,
                null,
                command.snapshotJson(),
                command.redactionProfile(),
                command.configVersionRefsJson(),
                command.result(),
                command.reasonCode(),
                command.occurredAt(),
                command.retentionUntil(),
                command.legalHold(),
                hash,
                previousHash);
    }

    private AuditRecordCommand command(UUID tenantId, UUID requestId, Instant occurredAt) {
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
                requestId,
                "source-ip-hash",
                "user-agent-hash",
                null,
                null,
                "{\"borrower\":\"REDACTED\"}".getBytes(StandardCharsets.UTF_8),
                "snapshot-key-ref",
                "mortgage-pricing-default-v1",
                "{\"retentionPolicy\":\"tenant-config-ref\"}".getBytes(StandardCharsets.UTF_8),
                "SUCCESS",
                null,
                occurredAt,
                LocalDate.parse("2033-06-07"),
                false,
                "idem-key");
    }

    private AuditRecordCommand changedSnapshotCommand(UUID tenantId, UUID requestId, Instant occurredAt) {
        AuditRecordCommand command = command(tenantId, requestId, occurredAt);
        return new AuditRecordCommand(
                command.tenantId(),
                command.action(),
                command.subjectType(),
                command.subjectId(),
                command.subjectVersion(),
                command.actorType(),
                command.actorId(),
                command.actorDisplay(),
                command.correlationId(),
                command.causationId(),
                command.requestId(),
                command.sourceIpHash(),
                command.userAgentHash(),
                command.beforeSnapshotJson(),
                command.afterSnapshotJson(),
                "{\"borrower\":\"CHANGED\"}".getBytes(StandardCharsets.UTF_8),
                command.snapshotEncryptionKeyRef(),
                command.redactionProfile(),
                command.configVersionRefsJson(),
                command.result(),
                command.reasonCode(),
                command.occurredAt(),
                command.retentionUntil(),
                command.legalHold(),
                command.idempotencyKey());
    }
}
