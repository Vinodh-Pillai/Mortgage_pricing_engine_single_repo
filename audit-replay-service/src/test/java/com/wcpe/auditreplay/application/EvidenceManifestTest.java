package com.wcpe.auditreplay.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.domain.AuditRecord;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenceManifestTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void hashesFilesInDeterministicOrder() throws Exception {
        UUID tenantId = UUID.randomUUID();
        AuditRecord later = record(tenantId, "quote-2", Instant.parse("2026-06-07T10:00:02Z"), "hash-later", false);
        AuditRecord earlier = record(tenantId, "quote-1", Instant.parse("2026-06-07T10:00:01Z"), "hash-earlier", true);
        EvidenceExportCommand command = command(tenantId, List.of(later.getId(), earlier.getId()));

        Map<String, Object> manifest = EvidenceExportManifest.build(command, List.of(later, earlier));
        String first = objectMapper.writeValueAsString(manifest);
        String second = objectMapper.writeValueAsString(EvidenceExportManifest.build(command, List.of(earlier, later)));

        assertEquals(first, second);
        assertEquals("evidence-export-manifest-v1", manifest.get("schemaVersion"));
        assertTrue((Boolean) manifest.get("legalHold"));
        assertTrue(first.indexOf("hash-earlier") < first.indexOf("hash-later"));
    }

    @Test
    void appliesRegulatorProfileWithoutRawBorrowerFields() throws Exception {
        UUID tenantId = UUID.randomUUID();
        AuditRecord record = record(tenantId, "quote-1", Instant.parse("2026-06-07T10:00:01Z"), "hash-1", false);

        String manifestJson = objectMapper.writeValueAsString(EvidenceExportManifest.build(command(tenantId, List.of(record.getId())), List.of(record)));

        assertTrue(manifestJson.contains("REGULATOR_SUMMARY"));
        org.junit.jupiter.api.Assertions.assertFalse(manifestJson.contains("Alice Applicant"));
        org.junit.jupiter.api.Assertions.assertFalse(manifestJson.contains("annualIncome"));
    }

    private EvidenceExportCommand command(UUID tenantId, List<UUID> sourceIds) {
        return new EvidenceExportCommand(
                tenantId,
                sourceIds,
                List.of(UUID.randomUUID()),
                "regulator package",
                "ZIP_JSON",
                "REGULATOR_SUMMARY",
                true,
                true,
                true,
                "audit-user-1",
                UUID.randomUUID(),
                null,
                "idem-evidence-export",
                null,
                Map.of());
    }

    private AuditRecord record(UUID tenantId, String subjectId, Instant occurredAt, String hash, boolean legalHold) {
        return AuditRecord.create(
                tenantId,
                UUID.randomUUID(),
                "QUOTE_REPLAY_COMPLETED",
                "quote",
                subjectId,
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
                "{\"borrower\":\"REDACTED\",\"quoteId\":\"quote-1\"}".getBytes(StandardCharsets.UTF_8),
                "REGULATOR_SUMMARY",
                "{\"retentionPolicy\":\"tenant-config-ref\"}".getBytes(StandardCharsets.UTF_8),
                "SUCCESS",
                null,
                occurredAt,
                LocalDate.parse("2033-06-07"),
                legalHold,
                hash,
                null);
    }
}
