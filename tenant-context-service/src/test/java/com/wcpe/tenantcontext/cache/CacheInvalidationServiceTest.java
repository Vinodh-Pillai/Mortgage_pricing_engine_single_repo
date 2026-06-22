package com.wcpe.tenantcontext.cache;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.ActorRef;
import com.wcpe.tenantcontext.RequestContext;
import com.wcpe.tenantcontext.TenantContext;
import com.wcpe.tenantcontext.audit.AuditLogService;
import com.wcpe.tenantcontext.audit.TestOnlyInMemoryAuditLogStore;
import com.wcpe.tenantcontext.outbox.TestOnlyInMemoryOutboxStore;
import com.wcpe.tenantcontext.outbox.OutboxWriter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

class CacheInvalidationServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-07T18:45:00Z"), ZoneOffset.UTC);
    private final TestOnlyInMemoryOutboxStore outboxStore = new TestOnlyInMemoryOutboxStore();
    private final TestOnlyInMemoryAuditLogStore auditStore = new TestOnlyInMemoryAuditLogStore();
    private final CacheInvalidationService service = new CacheInvalidationService(new OutboxWriter(outboxStore, CLOCK),
        new AuditLogService(auditStore, CLOCK), CLOCK);

    @Test
    void writesIdempotentCacheInvalidationEventAndAuditEvidenceWithoutPayloadValues() {
        UUID requestId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        TenantContext context = context("tenant-alpha");

        CacheInvalidationService.CacheInvalidationResult result = service.requestInvalidation(context, requestId,
            new InvalidationScope("domain", "tenant-context", 4, true, false), "CONFIG_CHANGED");

        assertThat(result.event().eventName()).isEqualTo(CacheInvalidationService.EVENT_NAME);
        assertThat(result.event().partitionKey()).isEqualTo("tenant-alpha:domain:tenant-context");
        assertThat(result.event().correlationId()).isEqualTo("correlation-1");
        assertThat(result.event().causationId()).isEqualTo("causation-1");
        assertThat(result.event().envelopeJson()).contains("\"tenantId\":\"tenant-alpha\"");
        assertThat(result.event().envelopeJson()).contains("\"correlationId\":\"correlation-1\"");
        assertThat(result.event().envelopeJson()).contains("\"causationId\":\"causation-1\"");
        assertThat(result.event().envelopeJson()).doesNotContain("borrower");
        assertThat(result.audit().record().action()).isEqualTo("CACHE_INVALIDATION_REQUESTED");
        assertThat(auditStore.listByTenant("tenant-alpha")).hasSize(1);
    }

    @Test
    void deniesWildcardTenantInvalidationWithoutBreakGlassApproval() {
        assertThatThrownBy(() -> service.requestInvalidation(context("tenant-alpha"), UUID.randomUUID(),
                new InvalidationScope("tenant", "*", 1, false, false), "OPS_REQUEST"))
            .isInstanceOf(CacheException.class)
            .extracting(error -> ((CacheException) error).code())
            .isEqualTo("CACHE_SCOPE_TOO_BROAD");
    }

    private static TenantContext context(String tenantId) {
        return new TenantContext(tenantId, new RequestContext("request-1", "trace-1", "correlation-1", "causation-1", "idem-1", "tenant-context-service"),
            new ActorRef("actor-1", "SERVICE_ACCOUNT"), List.of("ops"), List.of("audit:write"), "tenant-context-service");
    }
}
