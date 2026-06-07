package com.wcpe.tenantcontext.audit;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.ActorRef;
import com.wcpe.tenantcontext.RequestContext;
import com.wcpe.tenantcontext.TenantContext;
import com.wcpe.tenantcontext.event.DataClassification;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

class AuditLogServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-08T01:45:00Z"), ZoneOffset.UTC);
    private final InMemoryAuditLogStore store = new InMemoryAuditLogStore();
    private final AuditLogService service = new AuditLogService(store, CLOCK);

    @Test
    void recordsImmutableTenantScopedAuditWithDeterministicHashAndWrittenEvent() {
        UUID auditId = UUID.fromString("018f7c7e-9f3b-7cc2-a6db-2e3df7d1c105");

        AuditWriteResult result = service.record(context("tenant-alpha", AuditLogService.AUDIT_WRITE_SCOPE), command(auditId, "idem-1",
            "{\"status\":\"APPROVED\",\"tenantId\":\"tenant-alpha\"}"));

        assertThat(result.record().tenantId()).isEqualTo("tenant-alpha");
        assertThat(result.record().recordHash()).startsWith("sha256:");
        assertThat(result.record().previousHash()).isBlank();
        assertThat(result.event().eventName()).isEqualTo("AuditRecordWritten.v1");
        assertThat(result.event().payloadJson()).contains("\"auditId\":\"" + auditId + "\"");
        assertThat(result.event().payloadJson()).doesNotContain("borrower").doesNotContain("secret");

        AuditWriteResult replay = service.record(context("tenant-alpha", AuditLogService.AUDIT_WRITE_SCOPE), command(UUID.randomUUID(), "idem-1",
            "{\"tenantId\":\"tenant-alpha\",\"status\":\"APPROVED\"}"));
        assertThat(replay.record()).isEqualTo(result.record());
        assertThat(store.listByTenant("tenant-alpha")).hasSize(1);
    }

    @Test
    void redactsBorrowerPiiAndSecretsBeforePersistingAndHashing() {
        AuditRecord record = service.record(context("tenant-alpha", AuditLogService.AUDIT_WRITE_SCOPE), command(UUID.randomUUID(), "idem-2",
            "{\"tenantId\":\"tenant-alpha\",\"borrowerName\":\"Ada Lovelace\",\"secretToken\":\"abc123\",\"status\":\"DENIED\"}"))
            .record();

        assertThat(record.changeSummaryJson()).contains("\"borrowerName\":\"[REDACTED]\"");
        assertThat(record.changeSummaryJson()).contains("\"secretToken\":\"[REDACTED]\"");
        assertThat(record.changeSummaryJson()).doesNotContain("Ada Lovelace").doesNotContain("abc123");
    }

    @Test
    void rejectsTenantMismatchMissingScopeAndCrossTenantReads() {
        AuditRecord record = service.record(context("tenant-alpha", AuditLogService.AUDIT_WRITE_SCOPE), command(UUID.randomUUID(), "idem-3",
            "{\"tenantId\":\"tenant-alpha\",\"status\":\"READY\"}"))
            .record();

        assertThatThrownBy(() -> service.record(context("tenant-beta", AuditLogService.AUDIT_WRITE_SCOPE), command(UUID.randomUUID(), "idem-4",
            "{\"tenantId\":\"tenant-alpha\",\"status\":\"READY\"}")))
            .isInstanceOf(AuditException.class)
            .extracting(error -> ((AuditException) error).code())
            .isEqualTo("TENANT_ACCESS_DENIED");
        assertThatThrownBy(() -> service.get(context("tenant-alpha"), "tenant-alpha", record.auditId()))
            .isInstanceOf(AuditException.class)
            .extracting(error -> ((AuditException) error).code())
            .isEqualTo("TENANT_ACCESS_DENIED");
        assertThatThrownBy(() -> service.get(context("tenant-beta", AuditLogService.AUDIT_READ_SCOPE), "tenant-alpha", record.auditId()))
            .isInstanceOf(AuditException.class)
            .extracting(error -> ((AuditException) error).code())
            .isEqualTo("TENANT_ACCESS_DENIED");
    }

    @Test
    void detectsIdempotencyConflictsAndChainsPreviousHashPerTenant() {
        AuditRecord first = service.record(context("tenant-alpha", AuditLogService.AUDIT_WRITE_SCOPE), command(UUID.randomUUID(), "idem-5",
            "{\"tenantId\":\"tenant-alpha\",\"status\":\"READY\"}"))
            .record();

        assertThatThrownBy(() -> service.record(context("tenant-alpha", AuditLogService.AUDIT_WRITE_SCOPE), command(UUID.randomUUID(), "idem-5",
            "{\"tenantId\":\"tenant-alpha\",\"status\":\"CHANGED\"}")))
            .isInstanceOf(AuditException.class)
            .extracting(error -> ((AuditException) error).code())
            .isEqualTo("AUDIT_IDEMPOTENCY_CONFLICT");

        AuditRecord second = service.record(context("tenant-alpha", AuditLogService.AUDIT_WRITE_SCOPE), command(UUID.randomUUID(), "idem-6",
            "{\"tenantId\":\"tenant-alpha\",\"status\":\"DONE\"}"))
            .record();

        assertThat(second.previousHash()).isEqualTo(first.recordHash());
    }

    private static TenantContext context(String tenantId, String... scopes) {
        return new TenantContext(tenantId, new RequestContext("request-1", "trace-1", "correlation-1", "causation-1", "idem-context", "unit-test"),
            new ActorRef("actor-1", "USER"), List.of("compliance-officer"), List.of(scopes), "api");
    }

    private static AuditCommand command(UUID auditId, String idempotencyKey, String summaryJson) {
        return new AuditCommand("tenant-alpha", auditId, "PRICE_CONFIG_CHANGED", "Scenario", "scenario-1", "v1", "SUCCESS",
            "correlation-1", "causation-1", "event-1", idempotencyKey, "before-ref-1", "after-ref-1", summaryJson,
            DataClassification.CONFIDENTIAL);
    }
}
