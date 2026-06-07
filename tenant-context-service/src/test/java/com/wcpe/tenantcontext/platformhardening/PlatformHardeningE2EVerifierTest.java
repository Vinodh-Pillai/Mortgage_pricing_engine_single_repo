package com.wcpe.tenantcontext.platformhardening;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.TenantContextInput;
import com.wcpe.tenantcontext.TenantContextService;
import com.wcpe.tenantcontext.audit.AuditLogService;
import com.wcpe.tenantcontext.audit.InMemoryAuditLogStore;
import com.wcpe.tenantcontext.cache.CacheInvalidationService;
import com.wcpe.tenantcontext.cache.InvalidationScope;
import com.wcpe.tenantcontext.consumer.ConsumerGuardDecision;
import com.wcpe.tenantcontext.consumer.EventConsumerGuard;
import com.wcpe.tenantcontext.consumer.InMemoryConsumerInboxStore;
import com.wcpe.tenantcontext.health.DependencyStatus;
import com.wcpe.tenantcontext.health.HealthStatus;
import com.wcpe.tenantcontext.health.ReadinessAggregator;
import com.wcpe.tenantcontext.health.ReadinessComponent;
import com.wcpe.tenantcontext.outbox.InMemoryOutboxStore;
import com.wcpe.tenantcontext.outbox.OutboxWriter;
import com.wcpe.tenantcontext.ratelimit.EnforcementMode;
import com.wcpe.tenantcontext.ratelimit.InMemoryRateLimitCounter;
import com.wcpe.tenantcontext.ratelimit.RateLimitEvaluator;
import com.wcpe.tenantcontext.ratelimit.RateLimitOutcome;
import com.wcpe.tenantcontext.ratelimit.RateLimitPolicy;
import com.wcpe.tenantcontext.ratelimit.RedisUnavailableMode;
import com.wcpe.tenantcontext.ratelimit.PolicyStatus;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class PlatformHardeningE2EVerifierTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-08T04:00:00Z"), ZoneOffset.UTC);
    private static final UUID WORKFLOW_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void verifiesTenantIsolationOutboxAuditCacheReplayRateLimitReadinessAndRedactionTogether() {
        InMemoryOutboxStore outboxStore = new InMemoryOutboxStore();
        InMemoryAuditLogStore auditStore = new InMemoryAuditLogStore();
        InMemoryConsumerInboxStore inboxStore = new InMemoryConsumerInboxStore();
        OutboxWriter outboxWriter = new OutboxWriter(outboxStore, CLOCK);
        AuditLogService auditLogService = new AuditLogService(auditStore, CLOCK);
        PlatformHardeningE2EVerifier verifier = verifier(outboxWriter, auditLogService, inboxStore);

        PlatformHardeningE2EVerifier.PlatformHardeningEvidence evidence = verifier.verify(command("synthetic-tenant-alpha", validInput("synthetic-tenant-alpha"), List.of(
            "synthetic fixture uses tenant alpha only",
            "correlation corr-pii18-s10 links api audit outbox cache readiness"
        )));

        assertThat(evidence.tenantId()).isEqualTo("synthetic-tenant-alpha");
        assertThat(evidence.correlationId()).isEqualTo("corr-pii18-s10");
        assertThat(evidence.rateLimitDecision().outcome()).isEqualTo(RateLimitOutcome.ALLOWED);
        assertThat(evidence.cacheInvalidation().event().eventName()).isEqualTo(CacheInvalidationService.EVENT_NAME);
        assertThat(evidence.rateLimitEvent().eventName()).isEqualTo(PlatformHardeningE2EVerifier.RATE_LIMIT_EVENT_NAME);
        assertThat(evidence.audit().record().action()).isEqualTo("PLATFORM_HARDENING_E2E_VERIFIED");
        assertThat(evidence.firstConsumerOutcome()).isEqualTo(ConsumerGuardDecision.Outcome.PROCESSING_COMPLETED);
        assertThat(evidence.replayConsumerOutcome()).isEqualTo(ConsumerGuardDecision.Outcome.DUPLICATE_IGNORED);
        assertThat(evidence.readiness().status()).isEqualTo(HealthStatus.UP);
        assertThat(evidence.prohibitedEvidenceFindings()).isEmpty();
        assertThat(outboxStore.listByTenant("synthetic-tenant-alpha")).hasSize(2);
        assertThat(auditStore.listByTenant("synthetic-tenant-alpha")).hasSize(2);
        assertThat(inboxStore.listByTenant("synthetic-tenant-alpha")).hasSize(1);
    }

    @Test
    void failsClosedForCrossTenantAttemptBeforeWritingSideEffects() {
        InMemoryOutboxStore outboxStore = new InMemoryOutboxStore();
        InMemoryAuditLogStore auditStore = new InMemoryAuditLogStore();
        InMemoryConsumerInboxStore inboxStore = new InMemoryConsumerInboxStore();
        OutboxWriter outboxWriter = new OutboxWriter(outboxStore, CLOCK);
        AuditLogService auditLogService = new AuditLogService(auditStore, CLOCK);
        PlatformHardeningE2EVerifier verifier = verifier(outboxWriter, auditLogService, inboxStore);

        assertThatThrownBy(() -> verifier.verify(command("synthetic-tenant-beta", validInput("synthetic-tenant-alpha"), List.of("synthetic evidence"))))
            .extracting(error -> ((RuntimeException) error).getClass().getSimpleName())
            .isNotNull();

        assertThat(outboxStore.listByTenant("synthetic-tenant-alpha")).isEmpty();
        assertThat(auditStore.listByTenant("synthetic-tenant-alpha")).isEmpty();
        assertThat(inboxStore.listByTenant("synthetic-tenant-alpha")).isEmpty();
    }

    @Test
    void rejectsUnredactedSyntheticEvidenceMarkers() {
        PlatformHardeningE2EVerifier verifier = verifier(new OutboxWriter(new InMemoryOutboxStore(), CLOCK),
            new AuditLogService(new InMemoryAuditLogStore(), CLOCK), new InMemoryConsumerInboxStore());

        assertThatThrownBy(() -> verifier.verify(command("synthetic-tenant-alpha", validInput("synthetic-tenant-alpha"), List.of("Authorization header leaked"))))
            .isInstanceOf(PlatformHardeningException.class)
            .extracting(error -> ((PlatformHardeningException) error).code())
            .isEqualTo("PLATFORM_HARDENING_EVIDENCE_NOT_REDACTED");
    }

    private PlatformHardeningE2EVerifier verifier(OutboxWriter outboxWriter, AuditLogService auditLogService, InMemoryConsumerInboxStore inboxStore) {
        CacheInvalidationService cacheInvalidationService = new CacheInvalidationService(outboxWriter, auditLogService, CLOCK);
        return new PlatformHardeningE2EVerifier(new TenantContextService(), outboxWriter, auditLogService,
            cacheInvalidationService, new EventConsumerGuard(inboxStore, CLOCK),
            new PlatformHardeningE2EVerifier.RateLimitEvaluatorAdapter(new RateLimitEvaluator(new InMemoryRateLimitCounter())),
            readinessAggregator()::readiness, CLOCK);
    }

    private ReadinessAggregator readinessAggregator() {
        EnumMap<ReadinessComponent, com.wcpe.tenantcontext.health.DependencyProbe> probes = new EnumMap<>(ReadinessComponent.class);
        for (ReadinessComponent component : ReadinessComponent.values()) {
            probes.put(component, () -> DependencyStatus.up(component, component.name().toLowerCase() + " synthetic check passed", CLOCK.instant(), Map.of("evidence", "synthetic")));
        }
        return new ReadinessAggregator(probes, CLOCK);
    }

    private PlatformHardeningE2EVerifier.PlatformHardeningVerificationCommand command(String tenantId, TenantContextInput input, List<String> syntheticEvidence) {
        return new PlatformHardeningE2EVerifier.PlatformHardeningVerificationCommand(tenantId, WORKFLOW_ID, input,
            policy(tenantId), new InvalidationScope("rate-limit-policy", "policy-alpha", 1, false, false),
            "SYNTHETIC_PLATFORM_HARDENING", syntheticEvidence);
    }

    private RateLimitPolicy policy(String tenantId) {
        return new RateLimitPolicy(tenantId, UUID.fromString("22222222-2222-2222-2222-222222222222"), 1,
            "ops", "USER", "platform-hardening", 60, 5, 0, EnforcementMode.ENFORCE, PolicyStatus.ACTIVE,
            CLOCK.instant().minusSeconds(60), CLOCK.instant().plusSeconds(3600), "policy-author", "policy-approver",
            "SYNTHETIC_PLATFORM_HARDENING", RedisUnavailableMode.FAIL_CLOSED);
    }

    private TenantContextInput validInput(String tenantId) {
        return new TenantContextInput(tenantId, "request-pii18-s10", "trace-pii18-s10", "actor-pii18-s10", "USER",
            List.of("platform-admin"), List.of("tenant:context:read", "audit:write", "audit:read"), "ops",
            "corr-pii18-s10", "cause-pii18-s10", "idem-pii18-s10", "local-harness", List.of(tenantId), tenantId, "ACTIVE");
    }
}
