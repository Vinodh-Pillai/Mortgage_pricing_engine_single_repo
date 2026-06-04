package com.wcpe.quote;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QuoteApplicationService {
    private final QuoteRepository repository;
    private final QuoteDependencies dependencies;
    private final QuoteCache cache;
    private final BestExecutionRanker ranker;
    private final Clock clock;
    private final List<OutboxEvent> outboxEvents = new ArrayList<>();
    private final List<AuditEntry> auditEntries = new ArrayList<>();

    public QuoteApplicationService(
        QuoteRepository repository,
        QuoteDependencies dependencies,
        QuoteCache cache,
        BestExecutionRanker ranker,
        Clock clock
    ) {
        this.repository = repository;
        this.dependencies = dependencies;
        this.cache = cache;
        this.ranker = ranker;
        this.clock = clock;
    }

    public Quote createQuote(QuoteCreateRequest request) {
        validate(request);
        return repository.findByIdempotencyKey(request.tenantId(), request.idempotencyKey())
            .map(existing -> sameRequestOrConflict(existing, request))
            .orElseGet(() -> createNew(request));
    }

    public Quote getQuote(UUID tenantId, UUID quoteId) {
        Quote stored = repository.findById(tenantId, quoteId)
            .orElseThrow(() -> new QuoteCreateException("NOT_FOUND", "Quote not found"));
        return cache.get(tenantId, quoteId, stored.version()).orElse(stored);
    }

    public List<OutboxEvent> outboxEvents() {
        return List.copyOf(outboxEvents);
    }

    public List<AuditEntry> auditEntries() {
        return List.copyOf(auditEntries);
    }

    private Quote createNew(QuoteCreateRequest request) {
        RankingPolicy policy = dependencies.rankingPolicyFor(request)
            .orElseThrow(() -> new QuoteCreateException("NO_ACTIVE_RANKING_POLICY", "Missing effective-dated ranking policy"));
        Instant now = clock.instant();
        Instant expiresAt = now.plus(policy.quoteTtl());
        List<QuoteOption> options = ranker.rank(dependencies.candidatesFor(request), policy, expiresAt);
        QuoteStatus status = options.isEmpty() ? QuoteStatus.NO_OPTIONS : QuoteStatus.READY;
        QuoteInputVersionSet versionSet = new QuoteInputVersionSet(
            request.scenarioVersion(),
            dependencies.eligibilityVersion(),
            dependencies.pricingVersion(),
            dependencies.adjustmentVersion(),
            dependencies.marginVersion(),
            policy.policyVersion()
        );
        String replayHash = ReplayHash.sha256(normalizedReplayInput(request, versionSet, policy, options));
        Quote quote = new Quote(
            request.tenantId(),
            UUID.nameUUIDFromBytes((request.tenantId() + ":" + request.idempotencyKey()).getBytes()),
            request.scenarioId(),
            request.scenarioVersion(),
            status,
            policy.policyId(),
            policy.policyVersion(),
            versionSet,
            options,
            expiresAt,
            "audit:" + request.correlationId(),
            replayHash,
            request.idempotencyKey(),
            request.actorId(),
            now,
            request.correlationId(),
            1
        );
        repository.save(quote);
        cache.put(quote);
        recordAuditAndEvents(quote);
        return quote;
    }

    private Quote sameRequestOrConflict(Quote existing, QuoteCreateRequest request) {
        if (!existing.scenarioId().equals(request.scenarioId()) || existing.scenarioVersion() != request.scenarioVersion()) {
            throw new QuoteCreateException("DUPLICATE_IDEMPOTENCY_CONFLICT", "Idempotency key was used with different quote input");
        }
        return existing;
    }

    private static void validate(QuoteCreateRequest request) {
        if (request.tenantId() == null || request.scenarioId() == null || request.actorId() == null || request.actorId().isBlank()
            || request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new QuoteCreateException("QUOTE_VALIDATION_FAILED", "tenantId, scenarioId, actorId, and idempotencyKey are required");
        }
        if (request.scenarioVersion() < 1 || request.requestedLockPeriods().isEmpty()) {
            throw new QuoteCreateException("QUOTE_VALIDATION_FAILED", "scenarioVersion and requested lock periods are required");
        }
    }

    private static String normalizedReplayInput(
        QuoteCreateRequest request,
        QuoteInputVersionSet versionSet,
        RankingPolicy policy,
        List<QuoteOption> options
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tenantId", request.tenantId());
        values.put("scenarioId", request.scenarioId());
        values.put("scenarioVersion", request.scenarioVersion());
        values.put("lockPeriods", request.requestedLockPeriods());
        values.put("versions", versionSet.asMap());
        values.put("rankingPolicy", policy.policyId() + ":" + policy.policyVersion());
        values.put("options", options.stream().map(option -> option.rank() + ":" + option.productId() + ":" + option.finalPriceBps()).toList());
        return values.toString();
    }

    private void recordAuditAndEvents(Quote quote) {
        outboxEvents.add(event("quote.created.v1", quote));
        outboxEvents.add(event(quote.status() == QuoteStatus.NO_OPTIONS ? "quote.no_options.v1" : "quote.ready.v1", quote));
        auditEntries.add(new AuditEntry(
            "QUOTE_CREATE_REQUESTED",
            quote.createdBy(),
            quote.tenantId().toString(),
            quote.correlationId(),
            quote.replayHash(),
            quote.createdAt(),
            Map.of("status", "REQUESTED", "scenarioId", quote.scenarioId().toString())
        ));
        auditEntries.add(new AuditEntry(
            quote.status() == QuoteStatus.NO_OPTIONS ? "QUOTE_NO_OPTIONS" : "QUOTE_ORCHESTRATION_COMPLETED",
            quote.createdBy(),
            quote.tenantId().toString(),
            quote.correlationId(),
            quote.replayHash(),
            quote.createdAt(),
            Map.of("status", quote.status().name(), "quoteId", quote.quoteId().toString())
        ));
    }

    private static OutboxEvent event(String eventType, Quote quote) {
        return new OutboxEvent(
            eventType,
            "1",
            quote.tenantId() + ":" + quote.quoteId(),
            quote.createdAt(),
            Map.of(
                "tenantId", quote.tenantId().toString(),
                "eventType", eventType,
                "eventVersion", "1",
                "sourceService", "quote-service",
                "actorId", quote.createdBy(),
                "correlationId", quote.correlationId(),
                "idempotencyKey", quote.idempotencyKey()
            ),
            Map.of(
                "quoteId", quote.quoteId().toString(),
                "tenantId", quote.tenantId().toString(),
                "status", quote.status().name(),
                "version", Integer.toString(quote.version()),
                "replayHash", quote.replayHash()
            )
        );
    }
}
