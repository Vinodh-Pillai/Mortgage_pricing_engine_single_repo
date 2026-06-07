package com.wcpe.auditreplay.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.CorrelationContext;
import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.domain.LegalHoldStatus;
import com.wcpe.auditreplay.domain.RetentionPurgeRun;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import com.wcpe.auditreplay.repository.LegalHoldItemRepository;
import com.wcpe.auditreplay.repository.LegalHoldRepository;
import com.wcpe.auditreplay.repository.RetentionPolicyRepository;
import com.wcpe.auditreplay.repository.RetentionPurgeRunRepository;
import com.wcpe.auditreplay.vo.CausationId;
import com.wcpe.auditreplay.vo.CorrelationId;
import com.wcpe.auditreplay.vo.RequestId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PurgeEligibilityTest {

    @AfterEach
    void clearContext() {
        CorrelationContext.clear();
    }

    @Test
    void skipsHeldRecords() {
        UUID tenantId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        CorrelationContext.set(CorrelationId.of(correlationId.toString()), CausationId.ofNullable(null), RequestId.of("req-purge"));
        AuditRecord unheld = auditRecord(tenantId, false, LocalDate.parse("2025-01-01"));
        AuditRecord heldByRecord = auditRecord(tenantId, true, LocalDate.parse("2025-01-02"));
        AuditRecord heldByItem = auditRecord(tenantId, false, LocalDate.parse("2025-01-03"));
        AuditRecordRepository auditRecords = mock(AuditRecordRepository.class);
        LegalHoldItemRepository holdItems = mock(LegalHoldItemRepository.class);
        RetentionPurgeRunRepository purgeRuns = mock(RetentionPurgeRunRepository.class);
        when(purgeRuns.findByTenantIdAndIdempotencyKey(tenantId, "purge-idem")).thenReturn(Optional.empty());
        when(purgeRuns.save(any(RetentionPurgeRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditRecords.findAllByTenantIdAndRetentionUntilBeforeOrderByRetentionUntilAscCreatedAtAscIdAsc(
                tenantId, LocalDate.parse("2026-01-01"))).thenReturn(List.of(unheld, heldByRecord, heldByItem));
        when(holdItems.existsByTenantIdAndAuditRecordIdAndStatus(tenantId, unheld.getId(), LegalHoldStatus.ACTIVE)).thenReturn(false);
        when(holdItems.existsByTenantIdAndAuditRecordIdAndStatus(tenantId, heldByItem.getId(), LegalHoldStatus.ACTIVE)).thenReturn(true);
        RetentionLegalHoldService service = new RetentionLegalHoldService(
                mock(RetentionPolicyRepository.class),
                mock(LegalHoldRepository.class),
                holdItems,
                purgeRuns,
                auditRecords,
                mock(OutboxRecorder.class),
                new ObjectMapper().findAndRegisterModules());

        RetentionPurgeRun run = service.runPurge(new RetentionLegalHoldService.RetentionPurgeCommand(
                tenantId, LocalDate.parse("2026-01-01"), false, "retention-admin", correlationId, "purge-idem"));

        assertEquals(3, run.getCandidateCount());
        assertEquals(1, run.getPurgedCount());
        assertEquals(2, run.getSkippedHeldCount());
    }

    private AuditRecord auditRecord(UUID tenantId, boolean legalHold, LocalDate retentionUntil) {
        return AuditRecord.create(
                tenantId,
                UUID.randomUUID(),
                "AUDIT_CREATED",
                "QUOTE",
                UUID.randomUUID().toString(),
                1L,
                "USER",
                "audit-user",
                "Audit User",
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                "{\"field\":\"value\"}".getBytes(StandardCharsets.UTF_8),
                "DEFAULT",
                "{}".getBytes(StandardCharsets.UTF_8),
                "SUCCESS",
                null,
                Instant.parse("2025-01-01T00:00:00Z"),
                retentionUntil,
                legalHold,
                UUID.randomUUID().toString().replace("-", ""),
                null);
    }
}
