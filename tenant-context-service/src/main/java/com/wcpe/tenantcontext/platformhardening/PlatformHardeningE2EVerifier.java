package com.wcpe.tenantcontext.platformhardening;

import com.wcpe.tenantcontext.TenantContext;
import com.wcpe.tenantcontext.TenantContextInput;
import com.wcpe.tenantcontext.TenantContextService;
import com.wcpe.tenantcontext.audit.AuditCommand;
import com.wcpe.tenantcontext.audit.AuditLogService;
import com.wcpe.tenantcontext.audit.AuditWriteResult;
import com.wcpe.tenantcontext.cache.CacheInvalidationService;
import com.wcpe.tenantcontext.cache.InvalidationScope;
import com.wcpe.tenantcontext.consumer.ConsumerGuardDecision;
import com.wcpe.tenantcontext.consumer.EventConsumerGuard;
import com.wcpe.tenantcontext.event.DataClassification;
import com.wcpe.tenantcontext.health.ReadinessReport;
import com.wcpe.tenantcontext.outbox.OutboxEvent;
import com.wcpe.tenantcontext.outbox.OutboxEventCommand;
import com.wcpe.tenantcontext.outbox.OutboxWriter;
import com.wcpe.tenantcontext.ratelimit.RateLimitDecision;
import com.wcpe.tenantcontext.ratelimit.RateLimitEvaluator;
import com.wcpe.tenantcontext.ratelimit.RateLimitPolicy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public class PlatformHardeningE2EVerifier {
    public static final String RATE_LIMIT_EVENT_NAME = "RateLimitPolicyPublished.v1";
    public static final String RATE_LIMIT_TOPIC = "tenant-context.rate-limit-policy.v1";
    public static final String RATE_LIMIT_SCHEMA_REF = "mpe.security.RateLimitPolicyPublished.v1";
    public static final String CONSUMER_NAME = "platform-hardening-cache-invalidation";

    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern CONNECTION_STRING_PATTERN = Pattern.compile("(?i)\\b(jdbc|postgres|redis|mongodb|amqp|kafka)://[^\\s\"]+");
    private static final List<String> FORBIDDEN_MARKERS = List.of(
        "authorization", "bearer ", "cookie", "password", "secret", "access_token", "refresh_token", "api_key",
        "client_secret", "borrower", "customer", "ssn", "social-security"
    );

    private final TenantContextService tenantContextService;
    private final OutboxWriter outboxWriter;
    private final AuditLogService auditLogService;
    private final CacheInvalidationService cacheInvalidationService;
    private final EventConsumerGuard consumerGuard;
    private final RateLimitDecisionProvider rateLimitDecisionProvider;
    private final ReadinessProvider readinessProvider;
    private final Clock clock;

    public PlatformHardeningE2EVerifier(
        TenantContextService tenantContextService,
        OutboxWriter outboxWriter,
        AuditLogService auditLogService,
        CacheInvalidationService cacheInvalidationService,
        EventConsumerGuard consumerGuard,
        RateLimitDecisionProvider rateLimitDecisionProvider,
        ReadinessProvider readinessProvider,
        Clock clock
    ) {
        this.tenantContextService = require(tenantContextService, "tenantContextService");
        this.outboxWriter = require(outboxWriter, "outboxWriter");
        this.auditLogService = require(auditLogService, "auditLogService");
        this.cacheInvalidationService = require(cacheInvalidationService, "cacheInvalidationService");
        this.consumerGuard = require(consumerGuard, "consumerGuard");
        this.rateLimitDecisionProvider = require(rateLimitDecisionProvider, "rateLimitDecisionProvider");
        this.readinessProvider = require(readinessProvider, "readinessProvider");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public static PlatformHardeningE2EVerifier withRateLimitEvaluator(
        TenantContextService tenantContextService,
        OutboxWriter outboxWriter,
        AuditLogService auditLogService,
        CacheInvalidationService cacheInvalidationService,
        EventConsumerGuard consumerGuard,
        RateLimitEvaluatorAdapter rateLimitEvaluator,
        ReadinessProvider readinessProvider,
        Clock clock
    ) {
        return new PlatformHardeningE2EVerifier(tenantContextService, outboxWriter, auditLogService,
            cacheInvalidationService, consumerGuard, rateLimitEvaluator, readinessProvider, clock);
    }

    public PlatformHardeningEvidence verify(PlatformHardeningVerificationCommand command) {
        if (command == null) {
            throw new PlatformHardeningException("PLATFORM_HARDENING_COMMAND_REQUIRED", "verification command is required");
        }
        String tenantId = required(command.tenantId(), "tenantId");
        UUID workflowId = require(command.workflowId(), "workflowId");
        TenantContext tenantContext = tenantContextService.resolve(tenantId, require(command.tenantContextInput(), "tenantContextInput"));
        if (!tenantId.equals(tenantContext.tenantId())) {
            throw new PlatformHardeningException("TENANT_ACCESS_DENIED", "tenant context does not match platform hardening tenant");
        }

        Instant now = clock.instant();
        RateLimitDecision rateLimitDecision = rateLimitDecisionProvider.evaluate(require(command.rateLimitPolicy(), "rateLimitPolicy"), tenantContext, now);
        CacheInvalidationService.CacheInvalidationResult cacheInvalidation = cacheInvalidationService.requestInvalidation(
            tenantContext, workflowId, require(command.cacheScope(), "cacheScope"), required(command.reasonCode(), "reasonCode"));

        String payload = payload(tenantContext, workflowId, rateLimitDecision, cacheInvalidation.event().eventId(), now);
        OutboxEvent rateLimitEvent = writeRateLimitEvent(tenantContext, workflowId, payload, now);
        AuditWriteResult audit = writeAudit(tenantContext, workflowId, payload, rateLimitEvent.eventId());
        ConsumerGuardDecision firstConsume = consumerGuard.process(tenantContext, CONSUMER_NAME, consumerEnvelope(rateLimitEvent, payload, now), () -> cacheInvalidation.event().eventId().toString());
        ConsumerGuardDecision replayConsume = consumerGuard.process(tenantContext, CONSUMER_NAME, consumerEnvelope(rateLimitEvent, payload, now), () -> "duplicate-side-effect-not-allowed");
        ReadinessReport readiness = readinessProvider.readiness(tenantContext.request().correlationId());

        List<ProhibitedEvidenceFinding> findings = scan(List.of(payload, audit.record().changeSummaryJson(), rateLimitEvent.envelopeJson()), command.syntheticEvidenceSnippets());
        if (!findings.isEmpty()) {
            throw new PlatformHardeningException("PLATFORM_HARDENING_EVIDENCE_NOT_REDACTED", "synthetic evidence contains prohibited sensitive markers");
        }

        return new PlatformHardeningEvidence(tenantId, workflowId, tenantContext.request().correlationId(), rateLimitDecision,
            cacheInvalidation, rateLimitEvent, audit, firstConsume.outcome(), replayConsume.outcome(), readiness, findings);
    }

    private OutboxEvent writeRateLimitEvent(TenantContext tenantContext, UUID workflowId, String payload, Instant occurredAt) {
        UUID eventId = stableUuid("rate-limit:" + tenantContext.tenantId() + ":" + workflowId);
        return outboxWriter.write(tenantContext, new OutboxEventCommand(tenantContext.tenantId(), eventId,
            "RateLimitPolicy", workflowId.toString(), RATE_LIMIT_TOPIC, tenantContext.tenantId() + ":" + workflowId,
            RATE_LIMIT_SCHEMA_REF, RATE_LIMIT_EVENT_NAME, 1, payload, tenantContext.actor().actorId(),
            tenantContext.request().correlationId(), causationId(tenantContext, workflowId), idempotencyKey(tenantContext, workflowId, "rate-limit"), occurredAt));
    }

    private AuditWriteResult writeAudit(TenantContext tenantContext, UUID workflowId, String payload, UUID eventId) {
        return auditLogService.record(tenantContext, new AuditCommand(tenantContext.tenantId(),
            stableUuid("audit:platform-hardening:" + tenantContext.tenantId() + ":" + workflowId),
            "PLATFORM_HARDENING_E2E_VERIFIED", "PlatformHardeningE2E", workflowId.toString(), "1", "VERIFIED",
            tenantContext.request().correlationId(), causationId(tenantContext, workflowId), eventId.toString(),
            idempotencyKey(tenantContext, workflowId, "audit"), "synthetic-evidence-only", "outbox:" + eventId,
            payload, DataClassification.INTERNAL));
    }

    private com.wcpe.tenantcontext.consumer.EventEnvelope consumerEnvelope(OutboxEvent event, String payload, Instant occurredAt) {
        return new com.wcpe.tenantcontext.consumer.EventEnvelope(event.tenantId(), event.eventId(), event.eventName(),
            event.schemaRef(), event.eventVersion(), payload, event.correlationId(), event.causationId(), occurredAt);
    }

    private String payload(TenantContext tenantContext, UUID workflowId, RateLimitDecision decision, UUID cacheEventId, Instant occurredAt) {
        return "{"
            + "\"tenantId\":\"" + json(tenantContext.tenantId()) + "\","
            + "\"workflowId\":\"" + workflowId + "\","
            + "\"correlationId\":\"" + json(tenantContext.request().correlationId()) + "\","
            + "\"rateLimitOutcome\":\"" + decision.outcome().name() + "\","
            + "\"rateLimitStatus\":" + decision.httpStatus() + ","
            + "\"cacheInvalidationEventId\":\"" + cacheEventId + "\","
            + "\"evidenceType\":\"synthetic-platform-hardening\","
            + "\"occurredAt\":\"" + occurredAt + "\""
            + "}";
    }

    private List<ProhibitedEvidenceFinding> scan(List<String> generatedEvidence, List<String> suppliedEvidence) {
        List<ProhibitedEvidenceFinding> findings = new ArrayList<>();
        scanInto(findings, "generated", generatedEvidence);
        scanInto(findings, "synthetic", suppliedEvidence == null ? List.of() : suppliedEvidence);
        return List.copyOf(findings);
    }

    private void scanInto(List<ProhibitedEvidenceFinding> findings, String source, List<String> snippets) {
        for (String snippet : snippets) {
            if (snippet == null || snippet.isBlank()) {
                continue;
            }
            String lower = snippet.toLowerCase(Locale.ROOT);
            boolean marker = FORBIDDEN_MARKERS.stream().anyMatch(lower::contains);
            boolean structuredSecret = SSN_PATTERN.matcher(snippet).find() || CONNECTION_STRING_PATTERN.matcher(snippet).find();
            if (marker || structuredSecret) {
                findings.add(new ProhibitedEvidenceFinding(source, sanitize(snippet)));
            }
        }
    }

    private String sanitize(String value) {
        String compact = value.replaceAll("\\s+", " ").trim();
        if (compact.length() > 80) {
            compact = compact.substring(0, 80);
        }
        return compact.replaceAll("(?i)(authorization|bearer|cookie|password|secret|token|ssn|borrower|customer)[^,; ]*", "[REDACTED]");
    }

    private String causationId(TenantContext tenantContext, UUID workflowId) {
        return tenantContext.request().causationId().isBlank() ? workflowId.toString() : tenantContext.request().causationId();
    }

    private String idempotencyKey(TenantContext tenantContext, UUID workflowId, String suffix) {
        String base = tenantContext.request().idempotencyKey().isBlank() ? workflowId.toString() : tenantContext.request().idempotencyKey();
        return base + ":" + suffix;
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new PlatformHardeningException("PLATFORM_HARDENING_COMMAND_INVALID", fieldName + " is required");
        }
        return value.trim();
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new PlatformHardeningException("PLATFORM_HARDENING_COMMAND_INVALID", fieldName + " is required");
        }
        return value;
    }

    @FunctionalInterface
    public interface RateLimitDecisionProvider {
        RateLimitDecision evaluate(RateLimitPolicy policy, TenantContext context, Instant now);
    }

    public record RateLimitEvaluatorAdapter(RateLimitEvaluator evaluator) implements RateLimitDecisionProvider {
        public RateLimitEvaluatorAdapter {
            if (evaluator == null) {
                throw new PlatformHardeningException("PLATFORM_HARDENING_COMMAND_INVALID", "rateLimitEvaluator is required");
            }
        }

        @Override
        public RateLimitDecision evaluate(RateLimitPolicy policy, TenantContext context, Instant now) {
            return evaluator.evaluate(policy, context, now);
        }
    }

    @FunctionalInterface
    public interface ReadinessProvider {
        ReadinessReport readiness(String correlationId);
    }

    public record PlatformHardeningVerificationCommand(
        String tenantId,
        UUID workflowId,
        TenantContextInput tenantContextInput,
        RateLimitPolicy rateLimitPolicy,
        InvalidationScope cacheScope,
        String reasonCode,
        List<String> syntheticEvidenceSnippets
    ) { }

    public record PlatformHardeningEvidence(
        String tenantId,
        UUID workflowId,
        String correlationId,
        RateLimitDecision rateLimitDecision,
        CacheInvalidationService.CacheInvalidationResult cacheInvalidation,
        OutboxEvent rateLimitEvent,
        AuditWriteResult audit,
        ConsumerGuardDecision.Outcome firstConsumerOutcome,
        ConsumerGuardDecision.Outcome replayConsumerOutcome,
        ReadinessReport readiness,
        List<ProhibitedEvidenceFinding> prohibitedEvidenceFindings
    ) { }

    public record ProhibitedEvidenceFinding(String source, String sanitizedSnippet) { }
}
