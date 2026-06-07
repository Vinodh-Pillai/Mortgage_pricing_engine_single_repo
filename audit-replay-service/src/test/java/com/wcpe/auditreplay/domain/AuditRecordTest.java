package com.wcpe.auditreplay.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditRecordTest {

    @Test
    void requiresTenantActorActionCorrelation() {
        assertThrows(NullPointerException.class, () -> record(null, "QUOTE_CREATED", "SERVICE", UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> record(UUID.randomUUID(), " ", "SERVICE", UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> record(UUID.randomUUID(), "QUOTE_CREATED", " ", UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> record(UUID.randomUUID(), "QUOTE_CREATED", "SERVICE", null));

        AuditRecord record = record(UUID.randomUUID(), "QUOTE_CREATED", "SERVICE", UUID.randomUUID());

        assertNotNull(record.getId());
        assertEquals("QUOTE_CREATED", record.getAction());
        assertEquals("SERVICE", record.getActorType());
        assertEquals("sha-256-fixture", record.getIntegrityHash());
    }

    @Test
    void enforcesAppendOnlyLifecycle() {
        AuditRecord record = record(UUID.randomUUID(), "QUOTE_CREATED", "SERVICE", UUID.randomUUID());

        assertThrows(UnsupportedOperationException.class, record::rejectMutation);
    }

    private AuditRecord record(UUID tenantId, String action, String actorType, UUID correlationId) {
        return AuditRecord.create(
                tenantId,
                UUID.randomUUID(),
                action,
                "quote",
                "quote-123",
                1L,
                actorType,
                "quote-service",
                "Quote Service",
                correlationId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "source-ip-hash",
                "user-agent-hash",
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
                "sha-256-fixture",
                null);
    }
}
